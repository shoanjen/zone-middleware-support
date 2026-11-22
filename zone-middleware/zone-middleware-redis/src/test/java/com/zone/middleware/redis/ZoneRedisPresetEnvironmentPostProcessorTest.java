package com.zone.middleware.redis;

import com.zone.middleware.redis.config.ZoneRedisPresetEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneRedisPresetEnvironmentPostProcessorTest {

    private final ZoneRedisPresetEnvironmentPostProcessor processor = new ZoneRedisPresetEnvironmentPostProcessor();

    @Test
    void localModeShouldInjectDevelopmentPresets() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("zone.middleware.redis.mode", "LOCAL")));
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("2s", environment.getProperty("spring.data.redis.connect-timeout"));
        assertEquals("3s", environment.getProperty("spring.data.redis.timeout"));
        assertEquals("0", environment.getProperty("spring.data.redis.lettuce.pool.min-idle"));
    }

    @Test
    void cloudModeShouldInjectConservativePresets() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("zone.middleware.redis.mode", "CLOUD")));
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("10s", environment.getProperty("spring.data.redis.connect-timeout"));
        assertEquals("10s", environment.getProperty("spring.data.redis.timeout"));
    }

    @Test
    void explicitUserConfigShouldWinOverPresets() {
        StandardEnvironment environment = new StandardEnvironment();
        // addFirst：模拟 application.yml 中的显式配置（用户源优先级高于 addLast 的预设源）
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "zone.middleware.redis.mode", "CLOUD",
                "spring.data.redis.timeout", "500ms")));
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("500ms", environment.getProperty("spring.data.redis.timeout"), "显式官方键配置应覆盖预设");
        assertEquals("10s", environment.getProperty("spring.data.redis.connect-timeout"), "未显式配置项仍取预设");
    }

    @Test
    void modeMissingShouldDefaultToLocalPresets() {
        StandardEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("2s", environment.getProperty("spring.data.redis.connect-timeout"), "未声明 mode 时按 LOCAL 预设");
    }
}
