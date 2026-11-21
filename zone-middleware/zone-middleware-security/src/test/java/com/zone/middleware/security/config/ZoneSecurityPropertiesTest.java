package com.zone.middleware.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全配置项绑定单测：默认值与自定义覆盖。
 */
class ZoneSecurityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesEnablement.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZoneSecurityProperties.class)
    static class PropertiesEnablement {
    }

    @Test
    void shouldExposeDefaults() {
        runner.run(context -> {
            ZoneSecurityProperties properties = context.getBean(ZoneSecurityProperties.class);
            assertTrue(properties.getSign().isEnabled());
            assertNull(properties.getSign().getSecret());
            assertEquals(Duration.ofMinutes(5), properties.getSign().getTimestampWindow());
            assertEquals(Duration.ofDays(7), properties.getToken().getTtl());
            assertTrue(properties.getToken().isRenewSliding());
            assertTrue(properties.getToken().isMultiDevice());
        });
    }

    @Test
    void shouldBindCustomValues() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesEnablement.class)
                .withPropertyValues(
                        "zone.middleware.security.sign.enabled=false",
                        "zone.middleware.security.sign.secret=my-secret",
                        "zone.middleware.security.sign.timestamp-window=60s",
                        "zone.middleware.security.token.ttl=1h",
                        "zone.middleware.security.token.renew-sliding=false",
                        "zone.middleware.security.token.multi-device=false")
                .run(context -> {
                    ZoneSecurityProperties properties = context.getBean(ZoneSecurityProperties.class);
                    assertFalse(properties.getSign().isEnabled());
                    assertEquals("my-secret", properties.getSign().getSecret());
                    assertEquals(Duration.ofSeconds(60), properties.getSign().getTimestampWindow());
                    assertEquals(Duration.ofHours(1), properties.getToken().getTtl());
                    assertFalse(properties.getToken().isRenewSliding());
                    assertFalse(properties.getToken().isMultiDevice());
                });
    }
}
