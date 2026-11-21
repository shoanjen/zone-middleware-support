package com.zone.middleware.common.result;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.util.ParameterizedTypeImpl;
import com.zone.middleware.common.error.BusinessError;
import com.zone.middleware.common.error.SystemError;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneResultTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String name;
        private int age;
    }

    @Test
    void okShouldCarryDataAndSuccessCode() {
        ZoneResult<User> result = ZoneResult.ok(new User("zone", 3));
        assertTrue(result.isSuccess());
        assertEquals(ZoneResult.SUCCESS_CODE, result.getCode());
        assertEquals("成功", result.getMessage());
        assertEquals("zone", result.getData().getName());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void okWithoutDataShouldHaveNullData() {
        ZoneResult<Void> result = ZoneResult.ok();
        assertTrue(result.isSuccess());
        assertNull(result.getData());
    }

    @Test
    void failShouldUseErrorCodeAndMessage() {
        ZoneResult<Void> result = ZoneResult.fail(BusinessError.DATA_NOT_FOUND);
        assertFalse(result.isSuccess());
        assertEquals("BIZ-4004", result.getCode());
        assertEquals("数据不存在", result.getMessage());
    }

    @Test
    void failShouldAllowMessageOverride() {
        ZoneResult<Void> result = ZoneResult.fail(SystemError.CONFIG_ERROR, "缺少 redis 配置");
        assertFalse(result.isSuccess());
        assertEquals("SYS-1001", result.getCode());
        assertEquals("缺少 redis 配置", result.getMessage());
    }

    @Test
    void fastjson2RoundTripShouldKeepStructure() {
        ZoneResult<User> original = ZoneResult.ok(new User("zone", 3));

        String json = original.toJson();
        Type type = new ParameterizedTypeImpl(new Type[]{User.class}, null, ZoneResult.class);
        ZoneResult<User> parsed = JSON.parseObject(json, type);

        assertTrue(parsed.isSuccess());
        assertEquals(original.getCode(), parsed.getCode());
        assertEquals(original.getMessage(), parsed.getMessage());
        assertEquals(original.getTimestamp(), parsed.getTimestamp());
        assertEquals("zone", parsed.getData().getName());
        assertEquals(3, parsed.getData().getAge());
    }
}
