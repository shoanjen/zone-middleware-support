package com.zone.middleware.common.util;

import javax.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具：按主流代理头顺序解析真实客户端 IP，无有效代理头时回退 remoteAddr。
 */
public final class ZoneIpUtils {

    /** 无效代理头值的占位标识 */
    private static final String UNKNOWN = "unknown";

    /** 解析顺序：优先级从高到低 */
    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    /** 工具类禁止实例化 */
    private ZoneIpUtils() {
    }

    /**
     * 解析客户端真实 IP。
     * <p>按代理头优先级取第一个非空且非 {@code unknown} 的值；X-Forwarded-For 多级代理链取第一段并去除空白；
     * 所有代理头均无有效值时回退到 remoteAddr。</p>
     *
     * @param request 当前请求
     * @return 客户端 IP 字符串
     */
    public static String getClientIp(HttpServletRequest request) {
        for (String header : PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || UNKNOWN.equalsIgnoreCase(trimmed)) {
                continue;
            }
            // 多级代理：取第一段并去除空白
            int comma = trimmed.indexOf(',');
            String ip = (comma > 0 ? trimmed.substring(0, comma) : trimmed).trim();
            if (!ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }
}
