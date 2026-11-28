package com.zone.middleware.common.util;

import com.alibaba.fastjson2.TypeReference;
import com.zone.middleware.common.result.ZoneResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonUtilTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Order {
        private String orderNo;
        private int amount;
    }

    @Test
    void roundTripShouldKeepFields() {
        Order order = new Order("ORD-1", 99);

        String json = JsonUtil.toJson(order);
        Order parsed = JsonUtil.fromJson(json, Order.class);

        assertEquals("ORD-1", parsed.getOrderNo());
        assertEquals(99, parsed.getAmount());
    }

    @Test
    void typeReferenceShouldResolveFullGenericTree() {
        ZoneResult<Order> result = ZoneResult.ok(new Order("ORD-2", 5));
        String json = JsonUtil.toJson(result);

        ZoneResult<List<Order>> parsed = JsonUtil.fromJson(json,
                new TypeReference<ZoneResult<List<Order>>>() { });

        assertEquals(1, parsed.getData().size());
        assertEquals("ORD-2", parsed.getData().get(0).getOrderNo(),
                "泛型树应完整还原，data 不退化为 JSONObject");
    }

    @Test
    void toJsonShouldTolerateNull() {
        assertNull(JsonUtil.toJson(null));
    }

    @Test
    void fromJsonShouldTolerateNullOrEmpty() {
        assertNull(JsonUtil.fromJson(null, Order.class));
        assertNull(JsonUtil.fromJson("", Order.class));
        assertNull(JsonUtil.fromJson(null, new TypeReference<ZoneResult<Order>>() { }));
        assertNull(JsonUtil.fromJson("", new TypeReference<ZoneResult<Order>>() { }));
    }
}
