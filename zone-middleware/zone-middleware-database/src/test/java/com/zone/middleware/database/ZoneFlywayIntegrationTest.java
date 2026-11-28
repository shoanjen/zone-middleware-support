package com.zone.middleware.database;

import com.zone.middleware.database.config.ZoneFlywayConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneFlywayIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    ZoneFlywayConfiguration.class,
                    FlywayAutoConfiguration.class));

    @Test
    void shouldAutoMigrateFromDefaultLocationOnStartup() {
        runner.withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:zonefly;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa")
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
                        "spring.datasource.url=jdbc:h2:mem:zonefly2;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.flyway.locations=classpath:db/other")
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    Integer count = new JdbcTemplate(dataSource)
                            .queryForObject("SELECT COUNT(*) FROM zone_fly_other", Integer.class);
                    assertEquals(1, count, "自定义路径迁移应已执行");
                });
    }

    @Test
    void officialEnabledFalseShouldDisableMigration() {
        runner.withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:zonefly3;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.flyway.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("flywayInitializer"), "官方键关闭后不应装配 Flyway 初始化器");
                    assertTrue(context.getBean(DataSource.class) != null, "数据源不受影响");
                });
    }
}
