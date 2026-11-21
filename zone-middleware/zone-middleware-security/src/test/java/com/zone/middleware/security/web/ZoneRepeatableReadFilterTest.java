package com.zone.middleware.security.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求体可重读过滤器单测：POST 包装、GET 透传、body 校验后可重读。
 */
class ZoneRepeatableReadFilterTest {

    private final ZoneRepeatableReadFilter filter = new ZoneRepeatableReadFilter();

    @Test
    void shouldWrapPostRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/create");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertInstanceOf(ZoneCachedBodyRequestWrapper.class, chain.capturedRequest);
    }

    @Test
    void shouldPassThroughGetRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/query");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertFalse(chain.capturedRequest instanceof ZoneCachedBodyRequestWrapper);
        assertEquals(request, chain.capturedRequest);
    }

    @Test
    void shouldAllowDoubleBodyReadAfterWrap() throws Exception {
        String bodyJson = "{\"orderNo\":\"A1\"}";
        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/create");
        rawRequest.setContentType("application/json");
        rawRequest.setContent(bodyJson.getBytes(StandardCharsets.UTF_8));

        final String[] firstRead = new String[1];
        final String[] secondRead = new String[1];
        filter.doFilter(rawRequest, new MockHttpServletResponse(), (req, res) -> {
            ZoneCachedBodyRequestWrapper wrapped = (ZoneCachedBodyRequestWrapper) req;
            // 第一读（模拟拦截器经输入流消费）
            firstRead[0] = new String(wrapped.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // 第二读（模拟 Controller 再次经输入流读取，回放缓存）
            secondRead[0] = new String(wrapped.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        });
        assertEquals(bodyJson, firstRead[0]);
        assertEquals(bodyJson, secondRead[0]);
    }

    @Test
    void caseInsensitivePostMethodShouldBeWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("post", "/create");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertInstanceOf(ZoneCachedBodyRequestWrapper.class, chain.capturedRequest);
    }

    @Test
    void shouldMarkOverflowWhenBodyExceedsCacheLimit() throws Exception {
        byte[] oversized = new byte[1024 * 1024 + 1];
        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/create");
        rawRequest.setContentType("application/json");
        rawRequest.setContent(oversized);

        final boolean[] overflowed = new boolean[1];
        final int[] passthroughLength = new int[1];
        filter.doFilter(rawRequest, new MockHttpServletResponse(), (req, res) -> {
            ZoneCachedBodyRequestWrapper wrapped = (ZoneCachedBodyRequestWrapper) req;
            // 完整透传读取：返回值不被截断
            byte[] read = wrapped.getInputStream().readAllBytes();
            passthroughLength[0] = read.length;
            overflowed[0] = wrapped.isBodyOverflowed();
        });
        assertTrue(overflowed[0], "超出上限应置溢出标记");
        assertEquals(oversized.length, passthroughLength[0], "透传读取不应截断");
    }

    /** 捕获到达链尾的请求 */
    private static class CapturingChain implements FilterChain {
        private ServletRequest capturedRequest;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.capturedRequest = request;
        }
    }
}
