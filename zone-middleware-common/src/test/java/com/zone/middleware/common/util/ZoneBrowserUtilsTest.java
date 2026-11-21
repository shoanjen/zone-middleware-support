package com.zone.middleware.common.util;

import com.zone.middleware.common.entity.ZoneBrowserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneBrowserUtilsTest {

    private static final String CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";

    @Test
    void shouldParseChromeUserAgent() {
        ZoneBrowserInfo info = ZoneBrowserUtils.parseUserAgent(CHROME_UA);
        assertEquals("Chrome", info.getBrowser());
        assertEquals("114", info.getBrowserVersion());
        assertTrue(info.getOs().toLowerCase().contains("windows"));
        assertEquals("Computer", info.getDeviceType());
        assertTrue(!info.isMobile());
    }

    @Test
    void shouldParseMobileUserAgent() {
        String mobileUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1";
        ZoneBrowserInfo info = ZoneBrowserUtils.parseUserAgent(mobileUa);
        assertTrue(info.isMobile());
        assertEquals("Mobile", info.getDeviceType());    }

    @Test
    void shouldTolerateEmptyUserAgent() {
        ZoneBrowserInfo info = ZoneBrowserUtils.parseUserAgent("");
        assertEquals("Unknown", info.getBrowser());
        assertEquals("unknown", info.getBrowserVersion());

        ZoneBrowserInfo nullInfo = ZoneBrowserUtils.parseUserAgent(null);
        assertEquals("Unknown", nullInfo.getBrowser());
    }

    @Test
    void sameHeadersShouldProduceSameFingerprint() {
        MockHttpServletRequest first = requestWithHeaders();
        MockHttpServletRequest second = requestWithHeaders();
        assertEquals(ZoneBrowserUtils.fingerprint(first), ZoneBrowserUtils.fingerprint(second));
    }

    @Test
    void differentUserAgentShouldProduceDifferentFingerprint() {
        MockHttpServletRequest first = requestWithHeaders();

        MockHttpServletRequest second = requestWithHeaders();
        second.removeHeader("User-Agent");
        second.addHeader("User-Agent", CHROME_UA);

        assertNotEquals(ZoneBrowserUtils.fingerprint(first), ZoneBrowserUtils.fingerprint(second));
    }

    private MockHttpServletRequest requestWithHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15");
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        request.addHeader("Accept-Language", "zh-CN,zh;q=0.9");
        return request;
    }
}
