package com.zone.middleware.security.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.zone.middleware.common.constant.ZoneConstants;
import com.zone.middleware.common.enums.Platform;
import com.zone.middleware.common.error.BusinessError;
import com.zone.middleware.common.result.ZoneResult;
import com.zone.middleware.log.ZoneLog;
import com.zone.middleware.security.annotation.ZoneSignCheck;
import com.zone.middleware.security.config.ZoneSecurityProperties;
import com.zone.middleware.security.context.ZoneUserContext;
import com.zone.middleware.security.sign.ZoneSignUtils;
import com.zone.middleware.security.token.ZoneTokenService;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 接口签名校验拦截器：对 {@link ZoneSignCheck} 标注的端点执行校验链（fail-closed）。
 *
 * <p>校验顺序（签名比对先于 nonce 消费，垃圾签名请求无法抢占合法 reqId；登录态校验
 * 位于签名比对之后、nonce 消费之前，无效令牌请求同样不消耗 reqId）：</p>
 * <ol>
 *   <li>本端点有效时间窗：注解 {@code timestampWindowMillis}（毫秒）覆盖，缺省 0 继承
 *       全局配置；注解值为负 fail-closed 拒绝；</li>
 *   <li>请求头 {@code Zone-Access-Token} 非空；</li>
 *   <li>提取顶层参数（GET 为 query 参数，POST 为 query 参数 + JSON body 顶层）；</li>
 *   <li>reqId 非空；</li>
 *   <li>timestamp 有效时间窗校验；</li>
 *   <li>platform 分流：web 免 deviceId；pc/mobile/pad 必填且仅含字母数字下划线连字符；
 *       其余值拒绝；</li>
 *   <li>重建待签名串比对（常量时间比较）；</li>
 *   <li>登录态校验（requireLogin=true 时）：请求头 {@code Zone-Auth-Token} 解析登录态，
 *       通过后写入 {@code ZoneUserContext} request attribute（业务经
 *       {@code ZoneSecurityContext} 取用）；</li>
 *   <li>reqId nonce 防重放（Redis SETNX，TTL = 有效时间窗；存储不可用拒绝）。</li>
 * </ol>
 *
 * <p>任一环节失败：响应 HTTP 401 + 统一鉴权失败 {@code ZoneResult} JSON（不区分失败原因，
 * 审计原因仅记录服务端日志），请求不进入业务处理。</p>
 */
public class ZoneSignCheckInterceptor implements HandlerInterceptor {

    /** 类名（ZoneLog 埋点用） */
    private static final String CLASS_NAME = "ZoneSignCheckInterceptor";

    /** 参与签名的 BaseRequest 字段：请求标识 */
    private static final String REQ_ID_PARAM = "reqId";

    /** 参与签名的 BaseRequest 字段：请求时间戳 */
    private static final String TIMESTAMP_PARAM = "timestamp";

    /** 参与签名的 BaseRequest 字段：平台 */
    private static final String PLATFORM_PARAM = "platform";

    /** 参与签名的 BaseRequest 字段：设备标识 */
    private static final String DEVICE_ID_PARAM = "deviceId";

    /** JSON 请求体 Content-Type 前缀 */
    private static final String JSON_CONTENT_TYPE_PREFIX = "application/json";

    /** JSON 请求体大小上限（字节）：超限请求拒绝，防内存滥用 */
    private static final int MAX_JSON_BODY_BYTES = 1024 * 1024;

    /** 客户端 deviceId 合法形态：字母数字下划线连字符 */
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    /** nonce 存储 key 前缀 */
    private static final String NONCE_KEY_PREFIX = "zone:security:nonce:";

    /** nonce 占位值（仅用于占用 key，SETNX 语义） */
    private static final String NONCE_PLACEHOLDER = "1";

    /** 鉴权失败统一响应 JSON */
    private static final String UNAUTHORIZED_BODY =
            ZoneResult.fail(BusinessError.UNAUTHORIZED, "鉴权失败").toJson();

    /** 安全配置 */
    private final ZoneSecurityProperties properties;

    /** nonce 存储客户端（登录令牌与防重放的 Redis 底座模板） */
    private final StringRedisTemplate redisTemplate;

    /** 登录令牌服务（requireLogin=true 端点的登录态解析） */
    private final ZoneTokenService tokenService;

    /** 注解解析缓存：Spring 对每个路由缓存 HandlerMethod 单例，以其为 key 有界稳定 */
    private final Map<HandlerMethod, Optional<ZoneSignCheck>> annotationCache = new ConcurrentHashMap<>();

    /**
     * 构建签名校验拦截器。
     *
     * @param properties     zone.middleware.security.* 配置
     * @param redisTemplate  字符串 Redis 模板（nonce 存储）
     * @param tokenService   登录令牌服务（requireLogin=true 端点登录态解析）
     */
    public ZoneSignCheckInterceptor(ZoneSecurityProperties properties,
                                    StringRedisTemplate redisTemplate,
                                    ZoneTokenService tokenService) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.tokenService = tokenService;
    }

    /**
     * 未标注 {@link ZoneSignCheck} 的处理器直接放行；标注的执行校验链。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  目标处理器
     * @return true 表示放行；false 表示已写出 401 鉴权失败响应
     * @throws IOException 写响应失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        // CORS 预检不携带业务参数，交由 CORS 机制处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Optional<ZoneSignCheck> annotation = resolveAnnotation(handlerMethod);
        if (annotation.isEmpty()) {
            return true;
        }
        return verifySign(request, response, annotation.get());
    }

    /**
     * 解析处理器上的 {@link ZoneSignCheck}：方法级优先，其次类级；经合并注解查找，
     * 支持以本注解为元注解的组合注解。结果按 HandlerMethod 缓存（未标注端点缓存空结果，
     * 避免每请求反射探测）。
     *
     * @param handlerMethod 目标处理器方法
     * @return 方法或其所在类任一标注即包含该注解；未标注为 empty
     */
    private Optional<ZoneSignCheck> resolveAnnotation(HandlerMethod handlerMethod) {
        return annotationCache.computeIfAbsent(handlerMethod, method -> {
            ZoneSignCheck annotation =
                    AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ZoneSignCheck.class);
            if (annotation == null) {
                annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ZoneSignCheck.class);
            }
            return Optional.ofNullable(annotation);
        });
    }

    /**
     * 执行完整校验链。
     *
     * @param request    当前请求
     * @param response   当前响应
     * @param annotation 端点注解声明
     * @return true 校验通过；false 已写出拒绝响应
     * @throws IOException 写响应失败
     */
    private boolean verifySign(HttpServletRequest request,
                               HttpServletResponse response,
                               ZoneSignCheck annotation) throws IOException {
        String secret = properties.getSign().getSecret();
        // fail-closed：secret 未配置时全部拒绝（防止客户端以空 secret 绕过）
        if (!StringUtils.hasText(secret)) {
            return reject(response, "secret 未配置，签名校验全部拒绝");
        }

        // 1. 本端点有效时间窗（注解覆盖或继承全局；非法配置 fail-closed）
        Duration window = resolveEffectiveWindow(annotation, response);
        if (window == null) {
            return false;
        }

        // 2. 签名请求头
        String signHeader = request.getHeader(ZoneConstants.HEADER_ACCESS_TOKEN);
        if (!StringUtils.hasText(signHeader)) {
            return reject(response, "请求头缺失: " + ZoneConstants.HEADER_ACCESS_TOKEN);
        }

        // 3. 提取顶层参数（失败已在内部写出响应）
        Map<String, Object> params = extractParameters(request, response);
        if (params == null) {
            return false;
        }

        // 4. reqId
        String reqId = textParam(params, REQ_ID_PARAM);
        if (!StringUtils.hasText(reqId)) {
            return reject(response, "reqId 缺失或为空");
        }

        // 5. timestamp 有效时间窗
        if (!checkTimestampWindow(params, window, response)) {
            return false;
        }

        // 6. platform 分流与 deviceId 校验
        if (!checkPlatformAndDeviceId(params, response)) {
            return false;
        }

        // 7. 签名比对（常量时间比较，先于 nonce 消费防 reqId 抢占）
        String expectedSign = ZoneSignUtils.sign(params, secret);
        String actualSign = signHeader.trim().toLowerCase();
        if (!MessageDigest.isEqual(expectedSign.getBytes(StandardCharsets.UTF_8),
                actualSign.getBytes(StandardCharsets.UTF_8))) {
            return reject(response, "签名不一致");
        }

        // 8. 登录态校验（requireLogin=true：签名通过后、nonce 消费前，无效令牌不占用合法 reqId）
        if (annotation.requireLogin() && !checkLogin(request, response)) {
            return false;
        }

        // 9. nonce 防重放（存储不可用拒绝，fail-closed）
        return checkNonce(reqId, window, response);
    }

    /**
     * 解析本端点有效时间窗：注解 {@code timestampWindowMillis}（毫秒）为正值时覆盖全局，
     * 缺省 0 继承全局配置，负值属配置错误——fail-closed 拒绝并记录错误日志
     * （配置错误不能变成放行）。
     *
     * @param annotation 端点注解声明
     * @param response   当前响应
     * @return 有效时间窗；注解值为负返回 null（已写出拒绝响应）
     * @throws IOException 写响应失败
     */
    private Duration resolveEffectiveWindow(ZoneSignCheck annotation,
                                            HttpServletResponse response) throws IOException {
        long overrideMillis = annotation.timestampWindowMillis();
        if (overrideMillis == 0) {
            return properties.getSign().getTimestampWindow();
        }
        if (overrideMillis < 0) {
            ZoneLog.error(CLASS_NAME, "resolveEffectiveWindow",
                    "注解时间窗为负值, fail-closed 拒绝该端点请求: {}ms", overrideMillis);
            reject(response, "注解时间窗配置非法");
            return null;
        }
        return Duration.ofMillis(overrideMillis);
    }

    /**
     * 提取参与签名的顶层参数：GET 为 query 参数；POST 为 query 参数 + JSON body 顶层键值
     * （同名时 body 优先）。提取失败（非 JSON、超限、解析失败）时写出响应并返回 null。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @return 顶层参数（保持出现顺序）；失败返回 null
     * @throws IOException 写响应失败或读取 body 失败
     */
    private Map<String, Object> extractParameters(HttpServletRequest request,
                                                  HttpServletResponse response) throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String[] paramValues = entry.getValue();
            if (paramValues != null && paramValues.length > 0) {
                // 多值参数取首个（签名协议按单值参数约定）
                params.put(entry.getKey(), paramValues[0]);
            }
        }

        if (HttpMethod.POST.matches(request.getMethod())) {
            JSONObject bodyJson = readJsonBody(request, response);
            if (bodyJson == null) {
                return null;
            }
            params.putAll(bodyJson);
        }
        return params;
    }

    /**
     * 读取 POST JSON body 并解析为顶层对象：校验 Content-Type、大小上限（1MB），
     * 经 {@link ZoneCachedBodyRequestWrapper} 读取保证 Controller 可重读。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @return 顶层 JSON 对象；失败写出响应并返回 null
     * @throws IOException 读取失败
     */
    private JSONObject readJsonBody(HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith(JSON_CONTENT_TYPE_PREFIX)) {
            reject(response, "POST 请求须为 application/json");
            return null;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_JSON_BODY_BYTES) {
            reject(response, "请求体超过大小上限: " + MAX_JSON_BODY_BYTES + " 字节");
            return null;
        }

        ZoneCachedBodyRequestWrapper wrapper = WebUtils.getNativeRequest(request,
                ZoneCachedBodyRequestWrapper.class);
        if (wrapper == null) {
            reject(response, "请求未经可重读过滤器包装（装配缺失）");
            return null;
        }

        byte[] bodyBytes = wrapper.consumeBody();
        if (wrapper.isBodyOverflowed()) {
            reject(response, "请求体超过大小上限: " + MAX_JSON_BODY_BYTES + " 字节");
            return null;
        }

        try {
            JSONObject bodyJson = JSON.parseObject(new String(bodyBytes, StandardCharsets.UTF_8));
            if (bodyJson == null) {
                reject(response, "请求体 JSON 解析失败");
                return null;
            }
            return bodyJson;
        } catch (JSONException e) {
            reject(response, "请求体 JSON 解析失败");
            return null;
        }
    }

    /**
     * 读取字符串形态参数。
     *
     * @param params 顶层参数
     * @param key   参数名
     * @return 字符串值；缺失返回 null
     */
    private String textParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 校验 timestamp 时间窗：缺失、非数字、超出有效时间窗均拒绝。
     *
     * @param params   顶层参数
     * @param window   本端点有效时间窗
     * @param response 当前响应
     * @return true 通过；false 已写出拒绝响应
     * @throws IOException 写响应失败
     */
    private boolean checkTimestampWindow(Map<String, Object> params,
                                         Duration window,
                                         HttpServletResponse response) throws IOException {
        String timestampText = textParam(params, TIMESTAMP_PARAM);
        if (!StringUtils.hasText(timestampText)) {
            return reject(response, "timestamp 缺失或为空");
        }
        long requestTimestamp;
        try {
            requestTimestamp = Long.parseLong(timestampText.trim());
        } catch (NumberFormatException e) {
            return reject(response, "timestamp 非数字");
        }
        long deviation = Math.abs(System.currentTimeMillis() - requestTimestamp);
        if (deviation > window.toMillis()) {
            return reject(response, "timestamp 超出时间窗");
        }
        return true;
    }

    /**
     * 校验 platform 分流策略：web 免 deviceId；pc/mobile/pad 必填且格式校验；其余值拒绝。
     *
     * @param params   顶层参数
     * @param response 当前响应
     * @return true 通过；false 已写出拒绝响应
     * @throws IOException 写响应失败
     */
    private boolean checkPlatformAndDeviceId(Map<String, Object> params,
                                             HttpServletResponse response) throws IOException {
        Platform platform = Platform.of(textParam(params, PLATFORM_PARAM));
        if (platform == null) {
            return reject(response, "platform 非协议取值");
        }
        if (platform == Platform.WEB) {
            return true;
        }
        String deviceId = textParam(params, DEVICE_ID_PARAM);
        if (!StringUtils.hasText(deviceId) || !DEVICE_ID_PATTERN.matcher(deviceId.trim()).matches()) {
            return reject(response, "客户端策略 deviceId 缺失或格式非法");
        }
        return true;
    }

    /**
     * 登录态校验（requireLogin=true 端点）：读取请求头 {@code Zone-Auth-Token} 并经
     * {@link ZoneTokenService} 解析；令牌缺失、无效或存储不可用一律拒绝（fail-closed）。
     * 校验通过后构建 {@link ZoneUserContext} 写入 request attribute，业务代码经
     * {@code ZoneSecurityContext} 取用。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @return true 通过（已写入上下文）；false 已写出拒绝响应
     * @throws IOException 写响应失败
     */
    private boolean checkLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = ZoneAuthTokenResolver.resolve(request);
        if (token == null) {
            return reject(response, "登录令牌请求头缺失: " + ZoneConstants.HEADER_AUTH_TOKEN);
        }

        String userInfoJson;
        try {
            userInfoJson = tokenService.parseTokenRaw(token);
        } catch (RuntimeException e) {
            // 存储不可达等运行时异常与 nonce 路径同口径：fail-closed 拒绝而非放行
            ZoneLog.error(CLASS_NAME, "checkLogin", "登录令牌存储不可达，fail-closed 拒绝: {}", e.getMessage());
            return reject(response, "登录态存储不可用");
        }
        if (userInfoJson == null) {
            return reject(response, "登录令牌无效或已过期");
        }

        request.setAttribute(ZoneUserContext.REQUEST_ATTRIBUTE, new ZoneUserContext(token, userInfoJson));
        return true;
    }

    /**
     * 校验 reqId nonce 防重放：SETNX 占用成功即首次出现；占用失败为窗口内重放；
     * 存储不可用拒绝（fail-closed）。TTL 与本端点有效时间窗一致。
     *
     * @param reqId    请求标识
     * @param window   本端点有效时间窗
     * @param response 当前响应
     * @return true 通过；false 已写出拒绝响应
     * @throws IOException 写响应失败
     */
    private boolean checkNonce(String reqId, Duration window, HttpServletResponse response) throws IOException {
        String nonceKey = NONCE_KEY_PREFIX + reqId;
        Boolean occupied;
        try {
            occupied = redisTemplate.opsForValue()
                    .setIfAbsent(nonceKey, NONCE_PLACEHOLDER, window);
        } catch (RuntimeException e) {
            // DataAccessException 等：存储不可达一律拒绝而非放行
            ZoneLog.error(CLASS_NAME, "checkNonce", "nonce 存储不可达，fail-closed 拒绝: {}", e.getMessage());
            return reject(response, "nonce 存储不可用");
        }
        if (!Boolean.TRUE.equals(occupied)) {
            return reject(response, "reqId 窗口内重放");
        }
        return true;
    }

    /**
     * 写出统一的鉴权失败响应：HTTP 401 + {@code ZoneResult} JSON。
     * 审计原因仅记录服务端日志，响应体不区分失败原因。
     *
     * @param response    当前响应
     * @param auditReason 服务端审计原因（不含敏感参数值）
     * @return 恒为 false（调用方直接返回）
     * @throws IOException 写响应失败
     */
    private boolean reject(HttpServletResponse response, String auditReason) throws IOException {
        ZoneLog.warn(CLASS_NAME, "reject", "签名校验拒绝: {}", auditReason);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(UNAUTHORIZED_BODY);
        return false;
    }
}
