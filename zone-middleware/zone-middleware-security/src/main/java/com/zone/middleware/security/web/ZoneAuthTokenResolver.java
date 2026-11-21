package com.zone.middleware.security.web;

import com.zone.middleware.common.constant.ZoneConstants;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 登录令牌请求头取值工具：从当前请求读取 {@code Zone-Auth-Token} 请求头。
 *
 * <p>登录令牌经该请求头携带，业务层取值后交由 {@code ZoneTokenService#parseToken}
 * 解析登录态。</p>
 */
public final class ZoneAuthTokenResolver {

    /** 工具类禁止实例化 */
    private ZoneAuthTokenResolver() {
    }

    /**
     * 从请求头取登录令牌。
     *
     * @param request 当前请求
     * @return 令牌字符串；未携带或值为空返回 null
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader(ZoneConstants.HEADER_AUTH_TOKEN);
        return (token == null || token.isBlank()) ? null : token;
    }
}
