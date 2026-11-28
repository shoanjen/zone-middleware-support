package com.zone.middleware.security.web;

import com.alibaba.fastjson2.JSONObject;
import com.zone.middleware.common.constant.ZoneConstants;
import com.zone.middleware.log.ZoneLogLevel;
import com.zone.middleware.log.ZoneLogSettings;
import com.zone.middleware.security.annotation.ZoneSignCheck;
import com.zone.middleware.security.config.ZoneSecurityProperties;
import com.zone.middleware.security.context.ZoneSecurityContext;
import com.zone.middleware.security.context.ZoneUserContext;
import com.zone.middleware.security.sign.ZoneSignUtils;
import com.zone.middleware.security.token.ZoneTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签名校验拦截器单测：逐一覆盖 api-sign-auth 规格的校验链场景（nonce 以 mock 模拟），
 * 含注解属性（时间窗覆盖、强制登录）与组合注解场景（登录令牌解析以 mock 模拟）。
 */
class ZoneSignCheckInterceptorTest {

    /** 方法级标注样例控制器 */
    static class MethodAnnotatedController {
        @ZoneSignCheck
        public String annotated() {
            return "ok";
        }

        public String plain() {
            return "ok";
        }
    }

    /** 类级标注样例控制器 */
    @ZoneSignCheck
    static class ClassAnnotatedController {
        public String handle() {
            return "ok";
        }
    }

    /** 注解属性样例控制器：方法级时间窗覆盖与强制登录（毫秒） */
    static class AttributeAnnotatedController {
        @ZoneSignCheck(timestampWindowMillis = 10_000)
        public String tightWindow() {
            return "ok";
        }

        @ZoneSignCheck(timestampWindowMillis = 30_000, requireLogin = true)
        public String loginRequired() {
            return "ok";
        }

        @ZoneSignCheck(timestampWindowMillis = -1)
        public String negativeWindow() {
            return "ok";
        }
    }

    /** 类级时间窗 + 方法级覆盖样例控制器（毫秒） */
    @ZoneSignCheck(timestampWindowMillis = 600_000)
    static class ClassWindowController {
        public String classWindow() {
            return "ok";
        }

        @ZoneSignCheck(timestampWindowMillis = 10_000)
        public String overridden() {
            return "ok";
        }
    }

    /** 组合注解样例：以 @ZoneSignCheck 为元注解的业务注解 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ZoneSignCheck
    @interface ComposedSignCheck {
    }

    /** 组合注解标注样例控制器 */
    static class ComposedAnnotatedController {
        @ComposedSignCheck
        public String handle() {
            return "ok";
        }
    }

    /** 登录用户信息样例类型（fastjson2 反序列化目标） */
    static class TestUser {
        public String userId;
        public String name;
    }

    private static final String SECRET = "test-secret";

    private ZoneSecurityProperties properties;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ZoneTokenService tokenService;
    private ZoneSignCheckInterceptor interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new ZoneSecurityProperties();
        properties.getSign().setSecret(SECRET);

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        tokenService = mock(ZoneTokenService.class);

        interceptor = new ZoneSignCheckInterceptor(properties, redisTemplate, tokenService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ==================== 注解探测 ====================

    @Test
    void shouldPassHandlerWithoutAnnotation() throws Exception {
        MockHttpServletRequest request = signedWebGet("/plain", "r1");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "plain");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldCheckMethodAnnotatedEndpoint() throws Exception {
        MockHttpServletRequest request = signedWebGet("/annotated", "r2");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldCheckClassAnnotatedEndpoint() throws Exception {
        MockHttpServletRequest request = signedWebGet("/handle", "r3");
        HandlerMethod handler = handler(ClassAnnotatedController.class, "handle");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    // ==================== 请求头与签名 ====================

    @Test
    void shouldRejectMissingSignHeader() throws Exception {
        MockHttpServletRequest request = webGet("r4");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldRejectSignatureMismatch() throws Exception {
        MockHttpServletRequest request = webGet("r5");
        request.setParameter(ZoneConstants.HEADER_ACCESS_TOKEN, "00000000000000000000000000000000");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldLogSignatureComparisonElementsOnMismatch() throws Exception {
        // debug 门槛 + ListAppender 捕获签名比对三要素埋点
        ZoneLogSettings.apply("test-app", ZoneLogLevel.DEBUG, false, false, "test", null);
        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("ZoneSignCheckInterceptor");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logbackLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        logbackLogger.addAppender(appender);
        try {
            String wrongSign = "ffffffffffffffffffffffffffffffff";
            MockHttpServletRequest request = webGet("r-log");
            request.addHeader(ZoneConstants.HEADER_ACCESS_TOKEN, wrongSign);
            HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, handler));
            assertUnauthorized(response);

            // 拼接全部日志消息，断言三要素：传入 token、服务端参与参数、服务端计算签名
            StringBuilder allMessages = new StringBuilder();
            appender.list.forEach(event -> allMessages.append(event.getFormattedMessage()).append('\n'));
            String logs = allMessages.toString();

            assertTrue(logs.contains(wrongSign), "应包含传入的 zone-access-token 值: " + logs);
            assertTrue(logs.contains("\"reqId\":\"r-log\""),
                    "应包含服务端参与计算签名的请求参数: " + logs);
            assertTrue(logs.contains("服务端计算签名"), "应包含计算结果标注: " + logs);

            // 服务端计算签名值可独立复算验证
            Map<String, Object> expectedParams = new LinkedHashMap<>();
            expectedParams.put("reqId", "r-log");
            expectedParams.put("timestamp", request.getParameter("timestamp"));
            expectedParams.put("platform", "web");
            String expectedSign = ZoneSignUtils.sign(expectedParams, SECRET);
            assertTrue(logs.contains(expectedSign), "应包含服务端计算出的签名值: " + logs);
        } finally {
            logbackLogger.detachAppender(appender);
            // 恢复默认设置（reset 为包私有，跨包以等价 apply 还原）
            ZoneLogSettings.apply("unknown", ZoneLogLevel.INFO, true, true, "unknown", null);
        }
    }

    @Test
    void shouldRejectTamperedParameter() throws Exception {
        // 以 amount=100 计算签名并携带后，篡改为 999
        MockHttpServletRequest request = webGet("r6");
        request.setParameter("amount", "100");
        request.setParameter(ZoneConstants.HEADER_ACCESS_TOKEN, signOf(request));

        request.setParameter("amount", "999");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldRejectAllWhenSecretBlank() throws Exception {
        properties.getSign().setSecret("");
        interceptor = new ZoneSignCheckInterceptor(properties, redisTemplate, tokenService);

        MockHttpServletRequest request = signedWebGet("/annotated", "r7");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    // ==================== platform 分流 ====================

    @Test
    void shouldPassWebWithoutDeviceId() throws Exception {
        MockHttpServletRequest request = signedWebGet("/annotated", "r8");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldRejectClientWithoutDeviceId() throws Exception {
        MockHttpServletRequest request = signedClientGet("r9", "mobile", null);
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldRejectClientWithIllegalDeviceId() throws Exception {
        MockHttpServletRequest request = signedClientGet("r10", "pc", "dev@ice!");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldPassClientWithLegalDeviceId() throws Exception {
        MockHttpServletRequest request = signedClientGet("r11", "mobile", "device-01_abc");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldRejectUnknownPlatform() throws Exception {
        for (String unknown : new String[]{"watch", "", null}) {
            MockHttpServletRequest request = webGet("r12");
            if (unknown != null) {
                request.setParameter("platform", unknown);
            } else {
                request.removeParameter("platform");
            }
            request.setParameter(ZoneConstants.HEADER_ACCESS_TOKEN, signOf(request));
            HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, handler),
                    "未知 platform 应回绝: " + unknown);
            assertUnauthorized(response);
        }
    }

    // ==================== 时间窗 ====================

    @Test
    void shouldPassTimestampInsideWindow() throws Exception {
        // 3 分钟前（窗口默认 5 分钟）
        long timestamp = System.currentTimeMillis() - Duration.ofMinutes(3).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "r13", timestamp);

        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldRejectTimestampOutsideWindow() throws Exception {
        // 10 分钟前
        long timestamp = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "r14", timestamp);

        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldHonorConfiguredTimestampWindow() throws Exception {
        properties.getSign().setTimestampWindow(Duration.ofSeconds(60));
        interceptor = new ZoneSignCheckInterceptor(properties, redisTemplate, tokenService);

        // 2 分钟前：默认窗口内、60 秒窗口外
        long timestamp = System.currentTimeMillis() - Duration.ofMinutes(2).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "r15", timestamp);

        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldRejectMissingOrNonNumericTimestamp() throws Exception {
        MockHttpServletRequest request = webGet("r16");
        request.setParameter("timestamp", "not-a-number");
        request.setParameter(ZoneConstants.HEADER_ACCESS_TOKEN, signOf(request));
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    // ==================== nonce 防重放 ====================

    @Test
    void shouldRejectReplayedReqIdInWindow() throws Exception {
        // 第一次占用成功，第二次（重放）占用失败
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");
        assertTrue(interceptor.preHandle(signedWebGet("/annotated", "r17"),
                new MockHttpServletResponse(), handler));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(signedWebGet("/annotated", "r17"), response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldRejectWhenNonceStorageUnavailable() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        MockHttpServletRequest request = signedWebGet("/annotated", "r18");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    // ==================== 注解属性：时间窗覆盖 ====================

    @Test
    void shouldHonorMethodLevelTimestampWindow() throws Exception {
        // 1 分钟前：全局 5 分钟窗口内，但方法级 10 秒窗口外
        long timestamp = System.currentTimeMillis() - Duration.ofMinutes(1).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "w1", timestamp);
        HandlerMethod handler = handler(AttributeAnnotatedController.class, "tightWindow");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldPassInsideAnnotationWindow() throws Exception {
        // 5 秒前：方法级 10 秒窗口内
        long timestamp = System.currentTimeMillis() - Duration.ofSeconds(5).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "w2", timestamp);
        HandlerMethod handler = handler(AttributeAnnotatedController.class, "tightWindow");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldHonorClassLevelTimestampWindow() throws Exception {
        // 6 分钟前：全局 5 分钟窗口外，但类级 10 分钟窗口内——通过即证明走的是类级注解窗口
        long timestamp = System.currentTimeMillis() - Duration.ofMinutes(6).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "w3", timestamp);
        HandlerMethod handler = handler(ClassWindowController.class, "classWindow");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldLetMethodWindowOverrideClassWindow() throws Exception {
        // 6 分钟前：类级 10 分钟窗口内（全局 5 分钟窗口外），方法级覆盖后的 10 秒窗口外——拒绝即证明覆盖生效
        long timestamp = System.currentTimeMillis() - Duration.ofMinutes(6).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "w4", timestamp);
        HandlerMethod handler = handler(ClassWindowController.class, "overridden");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldUseEffectiveWindowForNonceTtl() throws Exception {
        // 5 秒前：注解 10 秒窗口内；nonce TTL 应为注解窗口 10s 而非全局 5m
        long timestamp = System.currentTimeMillis() - Duration.ofSeconds(5).toMillis();
        MockHttpServletRequest request = signedWebGet("/annotated", "w5", timestamp);
        HandlerMethod handler = handler(AttributeAnnotatedController.class, "tightWindow");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
        verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10)));
    }

    @Test
    void shouldRejectNegativeAnnotationWindow() throws Exception {
        // 注解窗口为负值（配置错误）：fail-closed 拒绝（即使签名合法）
        MockHttpServletRequest request = signedWebGet("/annotated", "w6");
        HandlerMethod handler = handler(AttributeAnnotatedController.class, "negativeWindow");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    // ==================== 强制登录（requireLogin） ====================

    @Test
    void shouldRejectLoginRequiredWithoutToken() throws Exception {
        MockHttpServletRequest request = signedWebGet("/annotated", "l1");
        HandlerMethod handler = handler(AttributeAnnotatedController.class, "loginRequired");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
        // 登录失败发生在 nonce 消费之前：reqId 未被占用
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldRejectLoginRequiredWithInvalidToken() throws Exception {
        when(tokenService.parseTokenRaw("bad-token")).thenReturn(null);
        MockHttpServletRequest request = signedWebGet("/annotated", "l2");
        request.addHeader(ZoneConstants.HEADER_AUTH_TOKEN, "bad-token");
        HandlerMethod handler = handler(AttributeAnnotatedController.class, "loginRequired");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldPassLoginRequiredWithValidTokenAndExposeContext() throws Exception {
        when(tokenService.parseTokenRaw("good-token"))
                .thenReturn("{\"userId\":\"u1\",\"name\":\"张三\"}");

        MockHttpServletRequest request = signedWebGet("/annotated", "l3");
        request.addHeader(ZoneConstants.HEADER_AUTH_TOKEN, "good-token");
        HandlerMethod handler = handler(AttributeAnnotatedController.class, "loginRequired");

        // 绑定请求上下文，验证门面可取用
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));

        // request attribute 写入与字段解析
        Object attribute = request.getAttribute(ZoneUserContext.REQUEST_ATTRIBUTE);
        assertTrue(attribute instanceof ZoneUserContext);
        ZoneUserContext context = (ZoneUserContext) attribute;
        assertEquals("u1", context.getUserId());
        assertEquals("good-token", context.getToken());

        // 静态门面取用：通用字段与业务类型解析
        assertEquals("u1", ZoneSecurityContext.currentUserId());
        assertEquals("good-token", ZoneSecurityContext.currentToken());
        TestUser user = ZoneSecurityContext.currentUser(TestUser.class);
        assertNotNull(user);
        assertEquals("u1", user.userId);
        assertEquals("张三", user.name);
    }

    @Test
    void shouldNotExposeContextOnPlainSignCheckEndpoint() throws Exception {
        // requireLogin=false 端点即使携带登录令牌也不解析、不写上下文
        MockHttpServletRequest request = signedWebGet("/annotated", "l4");
        request.addHeader(ZoneConstants.HEADER_AUTH_TOKEN, "ignored-token");
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
        assertNull(request.getAttribute(ZoneUserContext.REQUEST_ATTRIBUTE));
        assertNull(ZoneSecurityContext.currentUserId());
        verify(tokenService, never()).parseTokenRaw(anyString());
    }

    // ==================== 组合注解 ====================

    @Test
    void shouldSupportComposedAnnotation() throws Exception {
        MockHttpServletRequest request = signedWebGet("/annotated", "c1");
        HandlerMethod handler = handler(ComposedAnnotatedController.class, "handle");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldInterceptComposedAnnotationEndpointWithUnsignedRequest() throws Exception {
        HandlerMethod handler = handler(ComposedAnnotatedController.class, "handle");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(webGet("c2"), response, handler));
        assertUnauthorized(response);
    }

    // ==================== POST JSON body ====================

    @Test
    void shouldPassPostJsonBodySigning() throws Exception {
        Map<String, Object> signParams = baseWebParams("r19");
        signParams.put("orderNo", "A1001");
        signParams.put("amount", 100);

        JSONObject body = new JSONObject();
        body.put("reqId", "r19");
        body.put("timestamp", Long.parseLong(String.valueOf(signParams.get("timestamp"))));
        body.put("platform", "web");
        body.put("orderNo", "A1001");
        body.put("amount", 100);

        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/create");
        rawRequest.setContentType("application/json");
        rawRequest.setContent(body.toJSONString().getBytes(StandardCharsets.UTF_8));
        rawRequest.addHeader(ZoneConstants.HEADER_ACCESS_TOKEN, ZoneSignUtils.sign(signParams, SECRET));

        // 真实链路中过滤器先行包装
        HttpServletRequest request = new ZoneCachedBodyRequestWrapper(rawRequest);
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
    }

    @Test
    void shouldAllowControllerToRereadBodyAfterCheck() throws Exception {
        JSONObject body = new JSONObject();
        body.put("reqId", "r20");
        body.put("timestamp", System.currentTimeMillis());
        body.put("platform", "web");

        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/create");
        rawRequest.setContentType("application/json");
        byte[] bodyBytes = body.toJSONString().getBytes(StandardCharsets.UTF_8);
        rawRequest.setContent(bodyBytes);
        rawRequest.addHeader(ZoneConstants.HEADER_ACCESS_TOKEN,
                ZoneSignUtils.sign(new LinkedHashMap<>(body), SECRET));

        ZoneCachedBodyRequestWrapper request = new ZoneCachedBodyRequestWrapper(rawRequest);
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));

        // 拦截器消费后 Controller 再次经输入流读取（回放缓存）
        assertEquals(new String(bodyBytes, StandardCharsets.UTF_8),
                new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRejectPostWithNonJsonBody() throws Exception {
        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/create");
        rawRequest.setContentType("text/plain");
        rawRequest.setContent("hello".getBytes(StandardCharsets.UTF_8));
        rawRequest.addHeader(ZoneConstants.HEADER_ACCESS_TOKEN,
                "00000000000000000000000000000000");

        HttpServletRequest request = new ZoneCachedBodyRequestWrapper(rawRequest);
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    @Test
    void shouldRejectPostWithOversizedBody() throws Exception {
        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/create");
        // 声明超过 1MB 的 content-length（不实际分配内容）
        rawRequest.setContentType("application/json");
        rawRequest.addHeader("Content-Length", String.valueOf(1024 * 1024 + 1));
        rawRequest.setContent(new byte[0]);
        rawRequest.addHeader(ZoneConstants.HEADER_ACCESS_TOKEN,
                "00000000000000000000000000000000");

        HttpServletRequest request = new ZoneCachedBodyRequestWrapper(rawRequest);
        HandlerMethod handler = handler(MethodAnnotatedController.class, "annotated");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, handler));
        assertUnauthorized(response);
    }

    // ==================== 辅助 ====================

    /**
     * 构建 web 策略基础签名参数（reqId/timestamp/platform=web）。
     */
    private Map<String, Object> baseWebParams(String reqId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reqId", reqId);
        params.put("timestamp", System.currentTimeMillis());
        params.put("platform", "web");
        return params;
    }

    /**
     * 构建 GET 请求并按参数计算签名携带请求头。
     */
    private MockHttpServletRequest signedWebGet(String uri, String reqId) {
        return signedWebGet(uri, reqId, System.currentTimeMillis());
    }

    private MockHttpServletRequest signedWebGet(String uri, String reqId, long timestamp) {
        Map<String, Object> signParams = baseWebParams(reqId);
        signParams.put("timestamp", timestamp);

        MockHttpServletRequest request = webGet(reqId);
        request.setRequestURI(uri);
        request.setParameter("timestamp", String.valueOf(timestamp));
        request.addHeader(ZoneConstants.HEADER_ACCESS_TOKEN, ZoneSignUtils.sign(signParams, SECRET));
        return request;
    }

    /**
     * 构建携带基础参数（无签名头）的 GET 请求。
     */
    private MockHttpServletRequest webGet(String reqId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/query");
        request.setParameter("reqId", reqId);
        request.setParameter("timestamp", String.valueOf(System.currentTimeMillis()));
        request.setParameter("platform", "web");
        return request;
    }

    /**
     * 构建客户端策略（pc/mobile/pad）GET 请求：参数就位后按请求自身参数签名，杜绝时序偏差。
     */
    private MockHttpServletRequest signedClientGet(String reqId, String platform, String deviceId) {
        MockHttpServletRequest request = webGet(reqId);
        request.setParameter("platform", platform);
        if (deviceId != null) {
            request.setParameter("deviceId", deviceId);
        }
        request.addHeader(ZoneConstants.HEADER_ACCESS_TOKEN, signOf(request));
        return request;
    }

    /**
     * 按请求当前实际参数（每键取首值）计算签名。
     */
    private String signOf(MockHttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return ZoneSignUtils.sign(params, SECRET);
    }

    /**
     * 构建样例控制器的 HandlerMethod。
     */
    private HandlerMethod handler(Class<?> controllerType, String methodName) throws Exception {
        Method method = controllerType.getMethod(methodName);
        Object bean = controllerType.getDeclaredConstructor().newInstance();
        return new HandlerMethod(bean, method);
    }

    /**
     * 断言响应为统一 401 鉴权失败。
     */
    private void assertUnauthorized(MockHttpServletResponse response) throws Exception {
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8)
                .contains("\"success\":false"));
    }
}
