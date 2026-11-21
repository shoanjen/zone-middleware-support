package com.zone.middleware.database.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Flyway 整合：在 Spring Boot Flyway 自动配置之上提供 zone 默认值——
 * 未显式配置 spring.flyway.* 时，迁移路径默认 classpath:db/migration、baselineOnMigrate 默认 true；
 * zone.middleware.db.flyway.* 可覆盖默认值；enabled=false 时不注册定制（配合排除即不迁移）。
 */
@Configuration
@ConditionalOnClass(FluentConfiguration.class)
@AutoConfigureAfter(ZoneDataSourceAutoConfiguration.class)
@EnableConfigurationProperties(ZoneDatabaseProperties.class)
public class ZoneFlywayConfiguration {

    /**
     * 注册 Flyway 默认值定制器：未显式配置 spring.flyway.* 时套用 zone 默认。
     *
     * @param properties  zone 数据库配置（flyway 段）
     * @param environment 应用环境（判断 spring.flyway.* 是否显式配置）
     * @return Flyway 配置定制器
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "zone.middleware.db.flyway",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public FlywayConfigurationCustomizer zoneFlywayDefaultsCustomizer(
            ZoneDatabaseProperties properties, Environment environment) {
        return configuration -> {
            ZoneDatabaseProperties.Flyway flyway = properties.getFlyway();

            if (environment.getProperty("spring.flyway.locations") == null) {
                String locations = flyway.getLocations() != null
                        ? flyway.getLocations()
                        : "classpath:db/migration";
                configuration.locations(locations.split(","));
            }
            if (environment.getProperty("spring.flyway.baseline-on-migrate") == null) {
                boolean baseline = flyway.getBaselineOnMigrate() != null
                        && flyway.getBaselineOnMigrate();
                configuration.baselineOnMigrate(baseline);
            }
        };
    }
}
