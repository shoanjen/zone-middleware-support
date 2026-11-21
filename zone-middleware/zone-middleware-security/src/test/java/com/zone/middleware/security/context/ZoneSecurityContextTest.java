package com.zone.middleware.security.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 登录安全上下文门面单测：request attribute 取用、非 Web 线程与上下文缺失语义。
 */
class ZoneSecurityContextTest {

    /** 业务用户信息样例类型（fastjson2 反序列化目标） */
    static class SampleUser {
        public String userId;
        public String name;
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReadContextFromRequestAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ZoneUserContext.REQUEST_ATTRIBUTE,
                new ZoneUserContext("token-1", "{\"userId\":\"u1\",\"name\":\"张三\"}"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("u1", ZoneSecurityContext.currentUserId());
        assertEquals("token-1", ZoneSecurityContext.currentToken());

        SampleUser user = ZoneSecurityContext.currentUser(SampleUser.class);
        assertEquals("u1", user.userId);
        assertEquals("张三", user.name);
    }

    @Test
    void shouldReturnNullWhenContextAttributeAbsent() {
        // 已绑定请求上下文但未写登录上下文（如 requireLogin=false 端点）
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertNull(ZoneSecurityContext.currentUser());
        assertNull(ZoneSecurityContext.currentUser(SampleUser.class));
        assertNull(ZoneSecurityContext.currentUserId());
        assertNull(ZoneSecurityContext.currentToken());
    }

    @Test
    void shouldReturnNullOutsideWebRequestThread() {
        // 非 Web 请求线程（RequestContextHolder 未绑定）
        assertNull(ZoneSecurityContext.currentUser());
        assertNull(ZoneSecurityContext.currentUser(SampleUser.class));
        assertNull(ZoneSecurityContext.currentUserId());
        assertNull(ZoneSecurityContext.currentToken());
    }

    @Test
    void shouldReturnNullWhenAttributeTypeMismatched() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ZoneUserContext.REQUEST_ATTRIBUTE, "not-a-context");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertNull(ZoneSecurityContext.currentUser());
    }
}
