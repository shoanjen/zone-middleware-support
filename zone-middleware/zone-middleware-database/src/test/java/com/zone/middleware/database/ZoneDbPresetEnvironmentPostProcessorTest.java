package com.zone.middleware.database;

import com.zone.middleware.database.config.ZoneDbPresetEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneDbPresetEnvironmentPostProcessorTest {

    private final ZoneDbPresetEnvironmentPostProcessor processor = new ZoneDbPresetEnvironmentPostProcessor();

    @Test
    void localModeShouldInjectDevelopmentPresets() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("zone.middleware.db.mode", "LOCAL")));
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("5000", environment.getProperty("spring.datasource.hikari.connection-timeout"));
        assertEquals("10000", environment.getProperty("spring.datasource.hikari.leak-detection-threshold"));
        assertEquals("5000", environment.getProperty("spring.datasource.druid.max-wait"));
        assertEquals("2", environment.getProperty("spring.datasource.druid.initial-size"));
    }

    @Test
    void cloudModeShouldInjectConservativePresets() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("zone.middleware.db.mode", "CLOUD")));
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("15000", environment.getProperty("spring.datasource.hikari.connection-timeout"));
        assertEquals("0", environment.getProperty("spring.datasource.hikari.leak-detection-threshold"));
        assertEquals("120000", environment.getProperty("spring.datasource.hikari.keepalive-time"));
        assertEquals("15000", environment.getProperty("spring.datasource.druid.max-wait"));
        assertEquals("true", environment.getProperty("spring.datasource.druid.keep-alive"));
    }

    @Test
    void explicitUserConfigShouldWinOverPresets() {
        StandardEnvironment environment = new StandardEnvironment();
        // addFirst：模拟 application.yml 中的显式配置（用户源优先级高于 addLast 的预设源）
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "zone.middleware.db.mode", "CLOUD",
                "spring.datasource.hikari.connection-timeout", "8000")));
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("8000", environment.getProperty("spring.datasource.hikari.connection-timeout"),
                "显式官方键配置应覆盖预设");
        assertEquals("0", environment.getProperty("spring.datasource.hikari.leak-detection-threshold"),
                "未显式配置项仍取预设");
    }

    @Test
    void modeMissingShouldDefaultToLocalPresets() {
        StandardEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("5000", environment.getProperty("spring.datasource.hikari.connection-timeout"),
                "未声明 mode 时按 LOCAL 预设");
    }

    @Test
    void invalidModeShouldNotFailInPostProcessor() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("zone.middleware.db.mode", "MARS")));
        // 不抛错（非法 mode 留给装配期快速失败），按非 CLOUD 处理
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertEquals("5000", environment.getProperty("spring.datasource.hikari.connection-timeout"),
                "非法 mode 按 LOCAL 预设处理");
        assertEquals("10000", environment.getProperty("spring.datasource.hikari.leak-detection-threshold"),
                "应为 LOCAL 泄漏检测值而非 CLOUD 的 0");
    }
}
