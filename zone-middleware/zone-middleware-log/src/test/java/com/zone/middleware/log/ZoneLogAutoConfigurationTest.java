package com.zone.middleware.log;

import com.zone.middleware.log.config.ZoneLogAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneLogAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ZoneLogAutoConfiguration.class));

    @Test
    void shouldApplyDefaults() {
        runner.run(context -> {
            assertEquals("unknown", ZoneLogSettings.current().getAppName());
            assertEquals(ZoneLogLevel.INFO, ZoneLogSettings.current().getLevel());
        });
    }

    @Test
    void shouldApplyCustomProperties() {
        runner.withPropertyValues(
                        "spring.application.name=upstream-app",
                        "zone.middleware.log.level=DEBUG")
                .run(context -> {
                    // appName 未显式配置时回退 spring.application.name
                    assertEquals("upstream-app", ZoneLogSettings.current().getAppName());
                    assertEquals(ZoneLogLevel.DEBUG, ZoneLogSettings.current().getLevel());
                });
    }

    @Test
    void explicitAppNameShouldWinOverSpringName() {
        runner.withPropertyValues(
                        "spring.application.name=upstream-app",
                        "zone.middleware.log.app-name=zone-app",
                        "zone.middleware.log.include-trace-id=false")
                .run(context -> {
                    assertEquals("zone-app", ZoneLogSettings.current().getAppName());
                    assertEquals(false, ZoneLogSettings.current().isIncludeTraceId());
                });
    }
}
