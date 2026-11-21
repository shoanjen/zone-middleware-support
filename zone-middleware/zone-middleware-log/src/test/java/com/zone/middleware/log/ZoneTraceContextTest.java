package com.zone.middleware.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZoneTraceContextTest {

    @AfterEach
    void tearDown() {
        ZoneTraceContext.clear();
    }

    @Test
    void mdcKeysShouldMatchZoneProtocol() {
        assertEquals("traceId", ZoneTraceContext.MDC_KEY_TRACE_ID);
        assertEquals("reqId", ZoneTraceContext.MDC_KEY_REQ_ID);
    }

    @Test
    void reqIdShouldBeSetAndRead() {
        ZoneTraceContext.setReqId("r-001");
        assertEquals("r-001", ZoneTraceContext.getReqId());
    }

    @Test
    void emptyReqIdShouldBeIgnored() {
        ZoneTraceContext.setReqId("");
        assertNull(ZoneTraceContext.getReqId(), "空串 reqId 应被忽略");
    }

    @Test
    void laterReqIdShouldOverride() {
        ZoneTraceContext.setReqId("r-001");
        ZoneTraceContext.setReqId("r-002");
        assertEquals("r-002", ZoneTraceContext.getReqId());
    }

    @Test
    void bindShouldSetBothIds() {
        ZoneTraceContext.bind("t-100", "r-200");
        assertEquals("t-100", ZoneTraceContext.getTraceId());
        assertEquals("r-200", ZoneTraceContext.getReqId());
    }

    @Test
    void bindShouldIgnoreNullValues() {
        ZoneTraceContext.bind(null, null);
        assertNull(ZoneTraceContext.getTraceId());
        assertNull(ZoneTraceContext.getReqId());
    }

    @Test
    void clearShouldRemoveBothIds() {
        ZoneTraceContext.bind("t-100", "r-200");
        ZoneTraceContext.clear();
        assertNull(ZoneTraceContext.getTraceId());
        assertNull(ZoneTraceContext.getReqId());
    }
}
