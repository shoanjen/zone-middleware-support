package com.zone.middleware.http.interceptor;

import java.net.http.HttpRequest;

/**
 * 自定义鉴权扩展点：上游实现并注册为 Spring Bean 后，
 * {@link com.zone.middleware.http.ZoneHttpTemplate} 发起的每个请求都会经过该处理（如注入认证头）。
 */
@FunctionalInterface
public interface ZoneAuthInterceptor {

    /**
     * 对请求构建器做统一鉴权处理（设置 Header、签名等）。
     *
     * @param requestBuilder 请求构建器（可链式设置 header）
     */
    void apply(HttpRequest.Builder requestBuilder);
}
