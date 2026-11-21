package com.zone.middleware.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneConstantsTest {

    @Test
    void headerKeysShouldMatchZoneProtocol() {
        assertEquals("Zone-Access-Token", ZoneConstants.HEADER_ACCESS_TOKEN);
        assertEquals("Zone-Trace-Id", ZoneConstants.HEADER_TRACE_ID);
    }

    /** 模拟上层应用的领域常量接口：extends 后应自动继承内置通用常量 */
    interface DomainOrderConstants extends ZoneConstants {
        String ORDER_STATUS_PAID = "PAID";
    }

    @Test
    void derivedInterfaceShouldInheritBuiltinConstants() {
        assertEquals("Zone-Access-Token", DomainOrderConstants.HEADER_ACCESS_TOKEN);
        assertEquals("Zone-Trace-Id", DomainOrderConstants.HEADER_TRACE_ID);
        assertEquals("PAID", DomainOrderConstants.ORDER_STATUS_PAID);
    }
}
