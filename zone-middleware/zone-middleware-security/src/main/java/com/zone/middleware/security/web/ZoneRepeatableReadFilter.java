package com.zone.middleware.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求体可重读过滤器：POST 请求包装为 {@link ZoneCachedBodyRequestWrapper}。
 *
 * <p>签名校验（拦截器）需要读取 JSON body 参与签名，而 Servlet 规范下 body 只能被
 * 消费一次；本过滤器先行包装，拦截器读完缓存后，下游 Controller 仍可正常读取 body。
 * GET 请求无 body，直接透传。</p>
 */
public class ZoneRepeatableReadFilter extends OncePerRequestFilter {

    /** POST 方法名（大小写不敏感比较用统一大写形式） */
    private static final String METHOD_POST = "POST";

    /**
     * POST 请求包装为可重读请求后继续过滤链，其余方法透传。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param chain    过滤器链
     * @throws ServletException 过滤链后续处理异常
     * @throws IOException      读写异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (METHOD_POST.equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(new ZoneCachedBodyRequestWrapper(request), response);
            return;
        }
        chain.doFilter(request, response);
    }
}
