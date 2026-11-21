package com.zone.middleware.common.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneRandomUtilsTest {

    private static final Pattern ALPHANUMERIC = Pattern.compile("^[a-zA-Z0-9-]+$");
    private static final Pattern ALPHANUMERIC_PLAIN = Pattern.compile("^[a-zA-Z0-9]+$");

    @Test
    void shouldGenerateSegmentedStringWithExactTotalLength() {
        String value = ZoneRandomUtils.randomString(23, '-', 5);
        assertEquals(23, value.length());
        assertTrue(ALPHANUMERIC.matcher(value).matches(), "字符集应为字母数字+连接符: " + value);

        // 段与段之间恰好以 - 连接，每段不超过 5 个字符
        String[] segments = value.split("-", -1);
        for (String segment : segments) {
            assertTrue(segment.length() > 0 && segment.length() <= 5,
                    "分段长度应 ∈ (0,5]: " + value);
            assertTrue(Pattern.matches("[a-zA-Z0-9]+", segment));
        }
        assertEquals(4, segments.length, "23 长度 / (5+1) 应得 4 段: " + value);
    }

    @Test
    void shouldGeneratePlainStringWithoutConnector() {
        String value = ZoneRandomUtils.randomString(16, (String) null, 5);
        assertEquals(16, value.length());
        assertTrue(ALPHANUMERIC_PLAIN.matcher(value).matches());
    }

    @Test
    void plainOverloadShouldWork() {
        String value = ZoneRandomUtils.randomString(32);
        assertEquals(32, value.length());
        assertTrue(ALPHANUMERIC_PLAIN.matcher(value).matches());
    }

    @Test
    void emptyConnectorStringShouldNotSegment() {
        String value = ZoneRandomUtils.randomString(20, "", 4);
        assertEquals(20, value.length());
        assertTrue(ALPHANUMERIC_PLAIN.matcher(value).matches());
    }

    @Test
    void shouldSupportMultiCharConnector() {
        String value = ZoneRandomUtils.randomString(14, "--", 4);
        assertEquals(14, value.length());
        String[] segments = value.split("--", -1);
        assertEquals(3, segments.length, "14 = 4+2+4+2+2? 布局: " + value);
    }

    @Test
    void shouldThrowOnNonPositiveLength() {
        assertThrows(IllegalArgumentException.class, () -> ZoneRandomUtils.randomString(0));
        assertThrows(IllegalArgumentException.class, () -> ZoneRandomUtils.randomString(-3, '-', 5));
    }

    @Test
    void shouldThrowOnNonPositiveSegmentLength() {
        assertThrows(IllegalArgumentException.class, () -> ZoneRandomUtils.randomString(10, '-', 0));
        assertThrows(IllegalArgumentException.class, () -> ZoneRandomUtils.randomString(10, '-', -1));
    }

    @Test
    void shouldProduceDistinctValues() {
        // 碰撞概率极低，连续生成 100 个长度 16 的串应互不相同
        var set = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            set.add(ZoneRandomUtils.randomString(16));
        }
        assertEquals(100, set.size());
    }
}
