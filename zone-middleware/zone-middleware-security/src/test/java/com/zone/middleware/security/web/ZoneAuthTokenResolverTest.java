package com.zone.middleware.security.web;

import com.zone.middleware.common.constant.ZoneConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 登录令牌请求头取值单测。
 */
class ZoneAuthTokenResolverTest {

    @Test
    void shouldResolveTokenFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ZoneConstants.HEADER_AUTH_TOKEN, "token-abc");

        assertEquals("token-abc", ZoneAuthTokenResolver.resolve(request));
    }

    @Test
    void shouldReturnNullWhenHeaderMissingOrBlank() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        assertNull(ZoneAuthTokenResolver.resolve(missing));

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader(ZoneConstants.HEADER_AUTH_TOKEN, "   ");
        assertNull(ZoneAuthTokenResolver.resolve(blank));

        assertNull(ZoneAuthTokenResolver.resolve(null));
    }
}
