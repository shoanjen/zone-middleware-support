package com.zone.middleware.common.error;

import com.zone.middleware.common.exception.ZoneBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneErrorTest {

    @Test
    void systemErrorShouldUseSysPrefix() {
        for (SystemError error : SystemError.values()) {
            assertTrue(error.getCode().startsWith("SYS-"),
                    "系统错误码应为 SYS- 前缀: " + error.getCode());
            assertTrue(error.getCode().length() > "SYS-".length());
        }
        assertEquals("SYS-9999", SystemError.INTERNAL_ERROR.getCode());
    }

    @Test
    void businessErrorShouldUseBizPrefix() {
        for (BusinessError error : BusinessError.values()) {
            assertTrue(error.getCode().startsWith("BIZ-"),
                    "业务错误码应为 BIZ- 前缀: " + error.getCode());
        }
        assertEquals("BIZ-4004", BusinessError.DATA_NOT_FOUND.getCode());
    }

    @Test
    void codeSegmentsShouldNotOverlap() {
        for (SystemError system : SystemError.values()) {
            for (BusinessError business : BusinessError.values()) {
                assertTrue(!system.getCode().equals(business.getCode()),
                        "码段不应重叠: " + system.getCode());
            }
        }
    }

    @Test
    void bothEnumsAreZoneError() {
        ZoneError system = SystemError.DB_ERROR;
        ZoneError business = BusinessError.PARAM_INVALID;
        assertEquals("SYS-2001", system.getCode());
        assertEquals("BIZ-4001", business.getCode());
    }

    @Test
    void exceptionShouldCarryErrorAndDefaultMessage() {
        ZoneBusinessException exception = new ZoneBusinessException(SystemError.HTTP_ERROR);
        assertEquals(SystemError.HTTP_ERROR, exception.getError());
        assertEquals("HTTP请求失败", exception.getMessage());
    }

    @Test
    void exceptionShouldAllowMessageOverride() {
        ZoneBusinessException exception = new ZoneBusinessException(
                BusinessError.PARAM_INVALID, "订单号不能为空");
        assertEquals(BusinessError.PARAM_INVALID, exception.getError());
        assertEquals("BIZ-4001", exception.getError().getCode());
        assertEquals("订单号不能为空", exception.getMessage());
    }

    @Test
    void exceptionShouldBeRuntimeException() {
        assertThrows(ZoneBusinessException.class, () -> {
            throw new ZoneBusinessException(BusinessError.BUSINESS_FAILED);
        });
    }
}
