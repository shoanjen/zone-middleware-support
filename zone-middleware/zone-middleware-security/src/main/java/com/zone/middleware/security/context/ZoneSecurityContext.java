package com.zone.middleware.security.context;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录安全上下文门面：取用当前请求的登录用户信息。
 *
 * <p>数据来源为 {@code @ZoneSignCheck(requireLogin = true)} 端点校验通过后由拦截器写入的
 * {@link ZoneUserContext}（request attribute），经 Spring {@link RequestContextHolder} 读
 * 取——复用 Spring 自管理的请求线程上下文，无需手动清理；未声明强制登录的端点、上下文
 * 不存在或非 Web 请求线程时一律返回 null 而不抛出异常。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * @ZoneSignCheck(timestampWindowMillis = 60_000, requireLogin = true)
 * @PostMapping("/pay")
 * public ZoneResult<String> pay(@RequestBody PayRequest request) {
 *     String userId = ZoneSecurityContext.currentUserId();
 *     UserDTO user = ZoneSecurityContext.currentUser(UserDTO.class);
 *     ...
 * }
 * }</pre>
 */
public final class ZoneSecurityContext {

    /** 工具类禁止实例化 */
    private ZoneSecurityContext() {
    }

    /**
     * 取当前请求的登录用户上下文。
     *
     * @return 登录用户上下文；非 Web 请求线程、未声明强制登录的端点或上下文不存在返回 null
     */
    public static ZoneUserContext currentUser() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        Object value = servletAttributes.getRequest().getAttribute(ZoneUserContext.REQUEST_ATTRIBUTE);
        return value instanceof ZoneUserContext context ? context : null;
    }

    /**
     * 取当前登录用户信息并按业务类型解析。
     *
     * @param type 业务用户类型
     * @param <T>  用户信息类型
     * @return 用户信息；上下文不存在返回 null
     */
    public static <T> T currentUser(Class<T> type) {
        ZoneUserContext context = currentUser();
        return context == null ? null : context.as(type);
    }

    /**
     * 取当前登录用户标识（用户信息 JSON 的 userId 字段）。
     *
     * @return 用户标识；上下文不存在或 userId 字段缺失返回 null
     */
    public static String currentUserId() {
        ZoneUserContext context = currentUser();
        return context == null ? null : context.getUserId();
    }

    /**
     * 取当前登录令牌串（请求头 {@code Zone-Auth-Token} 携带值）。
     *
     * @return 登录令牌串；上下文不存在返回 null
     */
    public static String currentToken() {
        ZoneUserContext context = currentUser();
        return context == null ? null : context.getToken();
    }
}
