package com.zone.middleware.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZoneEnumsTest {

    @Test
    void platformShouldCoverVocabulary() {
        assertEquals(4, Platform.values().length);
        assertEquals("web", Platform.WEB.getValue());
        assertEquals("pc", Platform.PC.getValue());
        assertEquals("mobile", Platform.MOBILE.getValue());
        assertEquals("pad", Platform.PAD.getValue());
    }

    @Test
    void platformOfShouldBeCaseInsensitive() {
        assertEquals(Platform.WEB, Platform.of("web"));
        assertEquals(Platform.WEB, Platform.of("WEB"));
        assertEquals(Platform.WEB, Platform.of(" Web "));
        assertEquals(Platform.MOBILE, Platform.of("Mobile"));
    }

    @Test
    void platformOfUnknownShouldReturnNull() {
        assertNull(Platform.of("watch"));
        assertNull(Platform.of(null));
        assertNull(Platform.of(""));
        assertNull(Platform.of("  "));
    }

    @Test
    void osShouldCoverVocabulary() {
        assertEquals(5, OsType.values().length);
        assertEquals("android", OsType.ANDROID.getValue());
        assertEquals("ios", OsType.IOS.getValue());
        assertEquals("windows", OsType.WINDOWS.getValue());
        assertEquals("harmony", OsType.HARMONY.getValue());
        assertEquals("linux", OsType.LINUX.getValue());
    }

    @Test
    void osOfShouldBeCaseInsensitive() {
        assertEquals(OsType.ANDROID, OsType.of("android"));
        assertEquals(OsType.ANDROID, OsType.of("ANDROID"));
        assertEquals(OsType.HARMONY, OsType.of("Harmony"));
        assertEquals(OsType.IOS, OsType.of("iOS"));
    }

    @Test
    void osOfUnknownShouldReturnNull() {
        assertNull(OsType.of("macos"));
        assertNull(OsType.of(null));
        assertNull(OsType.of(""));
    }
}
