package com.zone.middleware.security.context;

import com.alibaba.fastjson2.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录用户上下文单测：字段透传、业务类型惰性解析与 userId 约定。
 */
class ZoneUserContextTest {

    /** 业务用户信息样例类型（fastjson2 反序列化目标） */
    static class SampleUser {
        public String userId;
        public String name;
    }

    @Test
    void shouldExposeTokenUserIdAndRawJson() {
        ZoneUserContext context = new ZoneUserContext("token-1", "{\"userId\":\"u1\",\"name\":\"张三\"}");

        assertEquals("token-1", context.getToken());
        assertEquals("u1", context.getUserId());
        assertEquals("{\"userId\":\"u1\",\"name\":\"张三\"}", context.getRawJson());
    }

    @Test
    void shouldParseUserInfoByBusinessType() {
        ZoneUserContext context = new ZoneUserContext("token-1", "{\"userId\":\"u1\",\"name\":\"张三\"}");

        SampleUser user = context.as(SampleUser.class);
        assertEquals("u1", user.userId);
        assertEquals("张三", user.name);
    }

    @Test
    void shouldTolerateMissingUserIdField() {
        // 用户信息 JSON 不含 userId 字段：userId 透传 null，不阻断
        ZoneUserContext context = new ZoneUserContext("token-1", "{\"name\":\"张三\"}");

        assertNull(context.getUserId());
        assertEquals("张三", context.as(SampleUser.class).name);
    }

    @Test
    void shouldTolerateMalformedJsonWithoutThrowing() {
        // 存储内容异常：构建时不抛出，userId 透传 null；业务类型解析时显式暴露
        ZoneUserContext context = new ZoneUserContext("token-1", "not-json");

        assertNull(context.getUserId());
        assertThrows(JSONException.class, () -> context.as(SampleUser.class));
    }
}
