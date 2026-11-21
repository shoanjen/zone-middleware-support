package com.zone.middleware.database;

import com.zone.middleware.database.config.ZoneDataSourceAutoConfiguration;
import com.zone.middleware.database.config.ZoneFlywayConfiguration;
import com.zone.middleware.database.config.ZoneFlywayEnablementEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneFlywayIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ZoneDataSourceAutoConfiguration.class,
                    ZoneFlywayConfiguration.class,
                    FlywayAutoConfiguration.class));

    @Test
    void shouldAutoMigrateFromDefaultLocationOnStartup() {
        runner.withPropertyValues(
                        "zone.middleware.db.url=jdbc:h2:mem:zonefly;DB_CLOSE_DELAY=-1",
                        "zone.middleware.db.username=sa")
                .run(context -> {
                    assertTrue(context.containsBean("flywayInitializer") || context.containsBean("flyway"),
                            "Flyway 应已装配");
                    DataSource dataSource = context.getBean(DataSource.class);
                    Integer count = new JdbcTemplate(dataSource)
                            .queryForObject("SELECT COUNT(*) FROM zone_fly_test", Integer.class);
                    assertEquals(1, count, "V1 迁移应已执行");
                });
    }

    @Test
    void shouldMigrateFromCustomLocation() {
        runner.withPropertyValues(
                        "zone.middleware.db.url=jdbc:h2:mem:zonefly2;DB_CLOSE_DELAY=-1",
                        "zone.middleware.db.username=sa",
                        "zone.middleware.db.flyway.locations=classpath:db/other")
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    Integer count = new JdbcTemplate(dataSource)
                            .queryForObject("SELECT COUNT(*) FROM zone_fly_other", Integer.class);
                    assertEquals(1, count, "自定义路径迁移应已执行");
                });
    }

    @Test
    void environmentPostProcessorShouldDisableBootFlywayWhenZoneDisabled() {
        ZoneFlywayEnablementEnvironmentPostProcessor processor =
                new ZoneFlywayEnablementEnvironmentPostProcessor();

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "zone.middleware.db.flyway.enabled", "false")));
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertEquals("false", environment.getProperty("spring.flyway.enabled"),
                "关闭 zone Flyway 时应短路 Boot 的 spring.flyway.enabled");

        // 上游显式配置 spring.flyway.enabled 时不覆盖
        StandardEnvironment explicit = new StandardEnvironment();
        explicit.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "zone.middleware.db.flyway.enabled", "false",
                "spring.flyway.enabled", "true")));
        processor.postProcessEnvironment(explicit, new SpringApplication());
        assertEquals("true", explicit.getProperty("spring.flyway.enabled"));

        // zone 未关闭时不注入
        StandardEnvironment enabled = new StandardEnvironment();
        enabled.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "zone.middleware.db.flyway.enabled", "true")));
        processor.postProcessEnvironment(enabled, new SpringApplication());
        assertTrue(enabled.getProperty("spring.flyway.enabled") == null);
        assertFalse(enabled.getPropertySources().contains("zoneFlywayDefaults"));
    }
}
