package com.zone.middleware.security.sign;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签名工具单测：期望值以独立 md5 命令行计算的摘要为基准，不依赖被测实现自身。
 */
class ZoneSignUtilsTest {

    @Test
    void signShouldSortParamsAscending() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("c", "3");

        // 独立基准：md5('a=1&b=2&c=3&key=s1') = d58dfa66c087302ca9adfc6344ade4a5
        assertEquals("d58dfa66c087302ca9adfc6344ade4a5",
                ZoneSignUtils.sign(params, "s1"));
    }

    @Test
    void signShouldExcludeNullAndEmptyValues() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", "");
        params.put("b", null);
        params.put("c", "3");

        // 独立基准：md5('c=3&key=s1') = d6f94533259823a01ee58ec4c4af3d65
        assertEquals("d6f94533259823a01ee58ec4c4af3d65",
                ZoneSignUtils.sign(params, "s1"));
    }

    @Test
    void signShouldSerializeNestedValueDeterministically() {
        JSONObject nested = new JSONObject();
        nested.put("orderNo", "A1");
        nested.put("items", JSONArray.of(1, 2));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("itemsParam", nested);

        // fastjson2 保插入序：md5('itemsParam={"orderNo":"A1","items":[1,2]}&key=s1')
        //                     = bf8854b80616336dc167a113b96d0c7f
        assertEquals("bf8854b80616336dc167a113b96d0c7f",
                ZoneSignUtils.sign(params, "s1"));
        // 同输入同输出
        assertEquals(ZoneSignUtils.sign(params, "s1"), ZoneSignUtils.sign(params, "s1"));
    }

    @Test
    void differentSecretShouldProduceDifferentSign() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", "1");

        assertNotEquals(ZoneSignUtils.sign(params, "secretA"), ZoneSignUtils.sign(params, "secretB"));
    }

    @Test
    void blankSecretShouldBeRejected() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", "1");

        assertThrows(IllegalArgumentException.class, () -> ZoneSignUtils.sign(params, null));
        assertThrows(IllegalArgumentException.class, () -> ZoneSignUtils.sign(params, ""));
    }

    @Test
    void generateReqIdShouldReturn32AlphaNumericWithoutConnector() {
        String reqId = ZoneSignUtils.generateReqId();
        assertEquals(32, reqId.length());
        assertTrue(reqId.matches("[a-zA-Z0-9]{32}"), "reqId 应为字母数字: " + reqId);
        assertFalse(reqId.contains("-"));

        assertNotEquals(reqId, ZoneSignUtils.generateReqId());
    }
}
