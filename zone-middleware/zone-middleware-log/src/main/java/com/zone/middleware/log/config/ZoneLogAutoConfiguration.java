package com.zone.middleware.log.config;

import com.zone.middleware.log.ZoneLog;
import com.zone.middleware.log.ZoneLogSettings;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ZoneLog 自动装配：绑定 zone.middleware.log.* 并应用到 {@link ZoneLogSettings}。
 * 环境阶段的早期应用见 {@link ZoneLogInitEnvironmentPostProcessor}；此处幂等重放兜底，
 * 保证任何装配路径下设置一致。
 */
@AutoConfiguration
@EnableConfigurationProperties(ZoneLogProperties.class)
public class ZoneLogAutoConfiguration {

    /**
     * 注册日志设置初始化器。
     *
     * @param properties  绑定的 zone.middleware.log.* 配置
     * @param environment 应用环境（spring.application.name 回退来源）
     * @return 单例装配完成后应用设置的初始化器
     */
    @Bean
    public ZoneLogInitializer zoneLogInitializer(ZoneLogProperties properties, Environment environment) {
        return new ZoneLogInitializer(properties, environment);
    }

    /** 单例装配完成后应用设置，应用启动完成前完成设置切换。 */
    static class ZoneLogInitializer implements SmartInitializingSingleton {

        private final ZoneLogProperties properties;
        private final Environment environment;

        /**
         * 构造初始化器。
         *
         * @param properties  绑定的 zone.middleware.log.* 配置
         * @param environment 应用环境
         */
        ZoneLogInitializer(ZoneLogProperties properties, Environment environment) {
            this.properties = properties;
            this.environment = environment;
        }

        /** 应用设置快照并输出启用日志（幂等，与早期 EPP 结果一致）。 */
        @Override
        public void afterSingletonsInstantiated() {
            String appName = properties.getAppName();
            if (appName == null || appName.isEmpty()) {
                appName = environment.getProperty("spring.application.name");
            }
            ZoneLogSettings.apply(appName, properties.getLevel(),
                    properties.isIncludeTraceId(), properties.isIncludeAppName(),
                    ZoneLogEnvResolver.resolveEnv(properties.getEnv(), environment),
                    properties.getFormat());

            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("appName", ZoneLogSettings.current().getAppName());
            settings.put("env", ZoneLogSettings.current().getEnv());
            settings.put("level", ZoneLogSettings.current().getLevel());
            settings.put("format", ZoneLogSettings.current().getFormat());
            settings.put("includeTraceId", properties.isIncludeTraceId());
            settings.put("includeAppName", properties.isIncludeAppName());
            ZoneLog.info("ZoneLogAutoConfiguration", "afterSingletonsInstantiated",
                    "zone-log enabled: {}", settings);
        }
    }
}
