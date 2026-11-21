package com.zone.middleware.log.config;

import com.zone.middleware.log.ZoneLogLevel;
import com.zone.middleware.log.ZoneLogFormat;
import com.zone.middleware.log.ZoneLogSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneLogInitEnvironmentPostProcessorTest {

    private final ZoneLogInitEnvironmentPostProcessor processor = new ZoneLogInitEnvironmentPostProcessor();

    @BeforeEach
    void setUp() {
        resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        resetToDefaults();
    }

    /** 复位为默认设置（reset 为包私有，跨包测试以公共 apply 等价复位）。 */
    private void resetToDefaults() {
        ZoneLogSettings.apply("unknown", ZoneLogLevel.INFO, true, true, "unknown", ZoneLogFormat.TEXT);
    }

    @Test
    void shouldApplyEnvFallbackFromProfilesFirstSegment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "app-a");
        environment.setProperty("spring.profiles.active", "stage,x");

        processor.postProcessEnvironment(environment, (SpringApplication) null);

        assertEquals("app-a", ZoneLogSettings.current().getAppName());
        assertEquals("stage", ZoneLogSettings.current().getEnv(), "env 应回退 profiles 首段");
        assertEquals(ZoneLogFormat.TEXT, ZoneLogSettings.current().getFormat());
    }

    @Test
    void shouldFallBackToUnknownWithoutAnySource() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, (SpringApplication) null);

        assertEquals("unknown", ZoneLogSettings.current().getEnv());
    }

    @Test
    void bindFailureShouldKeepCurrentSettingsAndNotCrash() {
        // 预置 JSON 设置；随后非法 format 绑定异常应保持现状且不抛出（AutoConfiguration 阶段负责启动失败）
        ZoneLogSettings.apply("pre", ZoneLogLevel.DEBUG, true, true, "pre-env", ZoneLogFormat.JSON);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("zone.middleware.log.format", "XML");

        processor.postProcessEnvironment(environment, (SpringApplication) null);

        assertEquals("pre", ZoneLogSettings.current().getAppName());
        assertEquals(ZoneLogFormat.JSON, ZoneLogSettings.current().getFormat(),
                "绑定异常应保持现有设置不变");
    }

    @Test
    void eppAndAutoConfigurationShouldApplyConsistentSettings() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "app-a");
        environment.setProperty("spring.profiles.active", "stage,x");
        environment.setProperty("zone.middleware.log.level", "DEBUG");

        // EPP 环境阶段应用
        processor.postProcessEnvironment(environment, (SpringApplication) null);
        String appName = ZoneLogSettings.current().getAppName();
        String env = ZoneLogSettings.current().getEnv();
        ZoneLogLevel level = ZoneLogSettings.current().getLevel();
        ZoneLogFormat format = ZoneLogSettings.current().getFormat();

        // AutoConfiguration 阶段幂等重放（同一配置）
        ZoneLogAutoConfiguration.ZoneLogInitializer initializer =
                new ZoneLogAutoConfiguration.ZoneLogInitializer(bindProperties(environment), environment);
        initializer.afterSingletonsInstantiated();

        assertEquals(appName, ZoneLogSettings.current().getAppName(), "两条装配链 appName 应一致");
        assertEquals(env, ZoneLogSettings.current().getEnv(), "两条装配链 env 应一致");
        assertEquals(level, ZoneLogSettings.current().getLevel(), "两条装配链 level 应一致");
        assertEquals(format, ZoneLogSettings.current().getFormat(), "两条装配链 format 应一致");
    }

    /**
     * 构造与 Binder 等价的属性对象（按当前环境绑定）。
     *
     * @param environment 测试环境
     * @return 绑定后的 ZoneLogProperties
     */
    private ZoneLogProperties bindProperties(MockEnvironment environment) {
        return org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("zone.middleware.log", org.springframework.boot.context.properties.bind.Bindable.of(ZoneLogProperties.class))
                .orElseGet(ZoneLogProperties::new);
    }
}
