package com.zone.middleware.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneIpUtilsTest {

    @Test
    void shouldTakeFirstIpFromXffChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        request.setRemoteAddr("10.0.0.2");
        assertEquals("203.0.113.5", ZoneIpUtils.getClientIp(request));
    }

    @Test
    void shouldFallBackToRemoteAddrWhenNoProxyHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        assertEquals("192.168.1.10", ZoneIpUtils.getClientIp(request));
    }

    @Test
    void shouldSkipUnknownValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "198.51.100.7");
        request.setRemoteAddr("10.0.0.2");
        assertEquals("198.51.100.7", ZoneIpUtils.getClientIp(request));
    }

    @Test
    void shouldSkipEmptyAndUnknownOnlyHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "");
        request.addHeader("X-Real-IP", "unknown");
        request.addHeader("Proxy-Client-IP", "  ");
        request.setRemoteAddr("172.16.0.9");
        assertEquals("172.16.0.9", ZoneIpUtils.getClientIp(request));
    }

    @Test
    void shouldTakeFirstSegmentWithSurroundingSpaces() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "  203.0.113.9 ,10.0.0.4");
        assertEquals("203.0.113.9", ZoneIpUtils.getClientIp(request));
    }
}
