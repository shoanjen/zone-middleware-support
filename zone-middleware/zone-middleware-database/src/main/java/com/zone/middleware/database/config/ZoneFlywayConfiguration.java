package com.zone.middleware.database.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Flyway 整合：在 Spring Boot Flyway 自动配置之上提供默认值——迁移行为全部由官方键
 * spring.flyway.* 控制；本定制器仅在官方键未显式配置时补默认（路径 classpath:db/migration、
 * 已有数据的库不自动基线化），上游显式配置总是优先。
 */
@AutoConfiguration
@ConditionalOnClass(FluentConfiguration.class)
@AutoConfigureAfter(ZoneDataSourceAutoConfiguration.class)
public class ZoneFlywayConfiguration {

    private static final String DEFAULT_LOCATIONS = "classpath:db/migration";

    /**
     * 注册 Flyway 默认值定制器：仅对未显式配置的官方键套默认值。
     *
     * @param environment 应用环境（判断 spring.flyway.* 是否显式配置）
     * @return Flyway 配置定制器
     */
    @Bean
    public FlywayConfigurationCustomizer zoneFlywayDefaultsCustomizer(Environment environment) {
        return configuration -> {
            if (environment.getProperty("spring.flyway.locations") == null) {
                configuration.locations(DEFAULT_LOCATIONS);
            }
            if (environment.getProperty("spring.flyway.baseline-on-migrate") == null) {
                configuration.baselineOnMigrate(false);
            }
        };
    }
}
