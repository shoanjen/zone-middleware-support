package com.zone.middleware.database.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Flyway 开关联动：zone.middleware.db.flyway.enabled=false 且上游未显式配置
 * spring.flyway.enabled 时，注入 spring.flyway.enabled=false 短路 Spring Boot 的
 * Flyway 自动配置（在自动配置条件求值之前执行）。
 */
public class ZoneFlywayEnablementEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String ZONE_ENABLED = "zone.middleware.db.flyway.enabled";
    private static final String SPRING_ENABLED = "spring.flyway.enabled";

    /**
     * zone 侧关闭 Flyway 且上游未显式配置 spring.flyway.enabled 时，
     * 注入短路属性使 Boot 的 Flyway 自动配置失效。
     *
     * @param environment 应用环境
     * @param application 启动应用实例
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String zoneEnabled = environment.getProperty(ZONE_ENABLED);
        if (zoneEnabled == null || Boolean.parseBoolean(zoneEnabled)) {
            return;
        }
        if (environment.getProperty(SPRING_ENABLED) != null) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "zoneFlywayDefaults", Map.of(SPRING_ENABLED, "false")));
    }

    /**
     * 获取执行顺序：尽量早执行，先于其他依赖该属性的环境处理。
     *
     * @return 优先级序数
     */
    @Override
    public int getOrder() {
        // 尽量早执行，先于其他依赖该属性的环境处理
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
