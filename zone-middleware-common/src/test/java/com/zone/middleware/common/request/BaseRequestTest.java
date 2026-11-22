package com.zone.middleware.common.request;

import com.alibaba.fastjson2.JSON;
import com.zone.middleware.common.enums.OsType;
import com.zone.middleware.common.enums.Platform;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseRequestTest {

    /** 模拟上游继承 BaseRequest 的业务请求 DTO */
    @Data
    @EqualsAndHashCode(callSuper = true)
    static class OrderQueryRequest extends BaseRequest {
        private String orderNo;
    }

    @Test
    void fullFieldRoundTrip() {
        BaseRequest request = new BaseRequest();
        request.setReqId("REQ-20261121-001");
        request.setPlatform("mobile");
        request.setIp("203.0.113.5");
        request.setDeviceId("DEV-9F3A");
        request.setOs("android");
        request.setVersion("1.0.0");
        request.setTimestamp(1732176000000L);

        String json = JSON.toJSONString(request);
        BaseRequest parsed = JSON.parseObject(json, BaseRequest.class);

        assertEquals("REQ-20261121-001", parsed.getReqId());
        assertEquals("mobile", parsed.getPlatform());
        assertEquals("203.0.113.5", parsed.getIp());
        assertEquals("DEV-9F3A", parsed.getDeviceId());
        assertEquals("android", parsed.getOs());
        assertEquals("1.0.0", parsed.getVersion());
        assertEquals(1732176000000L, parsed.getTimestamp());
    }

    @Test
    void legacySignFieldShouldBeIgnoredOnDeserialize() {
        // sign 字段已移除（签名一律经 Zone-Access-Token 请求头）：旧报文残留 sign 被 fastjson2 忽略
        BaseRequest parsed = JSON.parseObject(
                "{\"reqId\":\"REQ-006\",\"sign\":\"legacy-hash\",\"timestamp\":1}", BaseRequest.class);
        assertEquals("REQ-006", parsed.getReqId());
        assertEquals(1L, parsed.getTimestamp());
    }

    @Test
    void subclassShouldKeepBaseFieldsInRoundTrip() {
        OrderQueryRequest request = new OrderQueryRequest();
        request.setReqId("REQ-002");
        request.setPlatform("pc");
        request.setOs("windows");
        request.setOrderNo("ORD-77");

        String json = JSON.toJSONString(request);
        OrderQueryRequest parsed = JSON.parseObject(json, OrderQueryRequest.class);

        assertEquals("REQ-002", parsed.getReqId());
        assertEquals("pc", parsed.getPlatform());
        assertEquals("windows", parsed.getOs());
        assertEquals("ORD-77", parsed.getOrderNo());
    }

    @Test
    void platformAndOsShouldPassThroughUntouched() {
        // 载体层透传：未知值与大小写形态原样保留，不做枚举强转、不丢值
        BaseRequest parsed = JSON.parseObject(
                "{\"reqId\":\"REQ-003\",\"platform\":\"Web\",\"os\":\"macOS\"}", BaseRequest.class);

        assertEquals("Web", parsed.getPlatform(), "大小写形态应原样保留");
        assertEquals("macOS", parsed.getOs(), "未知系统值应原样透传");
    }

    @Test
    void upperLayerShouldResolveEnumsFromRawStrings() {
        BaseRequest request = JSON.parseObject(
                "{\"reqId\":\"REQ-004\",\"platform\":\"web\",\"os\":\"harmony\"}", BaseRequest.class);

        // 枚举是上层解析工具：of() 容错解析原始字符串
        assertEquals(Platform.WEB, Platform.of(request.getPlatform()));
        assertEquals(OsType.HARMONY, OsType.of(request.getOs()));
        assertNull(Platform.of("watch"), "未知值由上层拿到 null 自行决策");
    }

    @Test
    void absentOptionalFieldsShouldStayNull() {
        BaseRequest parsed = JSON.parseObject("{\"reqId\":\"REQ-005\"}", BaseRequest.class);

        assertEquals("REQ-005", parsed.getReqId());
        assertNull(parsed.getPlatform());
        assertNull(parsed.getOs());
        assertNull(parsed.getTimestamp());
    }
}
