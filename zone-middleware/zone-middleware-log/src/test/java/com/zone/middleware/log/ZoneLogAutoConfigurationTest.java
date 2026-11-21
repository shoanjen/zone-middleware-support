package com.zone.middleware.log;

import com.zone.middleware.log.config.ZoneLogAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneLogAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ZoneLogAutoConfiguration.class));

    @Test
    void shouldApplyDefaults() {
        runner.run(context -> {
            assertEquals("unknown", ZoneLogSettings.current().getAppName());
            assertEquals(ZoneLogLevel.INFO, ZoneLogSettings.current().getLevel());
            assertEquals("unknown", ZoneLogSettings.current().getEnv());
            assertEquals(ZoneLogFormat.TEXT, ZoneLogSettings.current().getFormat());
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

    @Test
    void envShouldFallBackToFirstSpringProfile() {
        runner.withPropertyValues("spring.profiles.active=dev,local")
                .run(context ->
                        assertEquals("dev", ZoneLogSettings.current().getEnv(),
                                "未配置 env 时应回退 spring.profiles.active 首段"));
    }

    @Test
    void explicitEnvShouldWinOverSpringProfiles() {
        runner.withPropertyValues(
                        "spring.profiles.active=dev,local",
                        "zone.middleware.log.env=prod")
                .run(context ->
                        assertEquals("prod", ZoneLogSettings.current().getEnv(),
                                "显式 env 应优先于 spring.profiles.active"));
    }

    @Test
    void jsonFormatShouldBeApplied() {
        runner.withPropertyValues("zone.middleware.log.format=JSON")
                .run(context ->
                        assertEquals(ZoneLogFormat.JSON, ZoneLogSettings.current().getFormat()));
    }

    @Test
    void illegalFormatShouldFailStartup() {
        runner.withPropertyValues("zone.middleware.log.format=XML")
                .run(context -> assertThat(context).hasFailed());
    }
}
