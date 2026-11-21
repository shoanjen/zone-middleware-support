package com.zone.middleware.database;

import com.alibaba.druid.pool.DruidDataSource;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.database.config.ZoneDataSourceAutoConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneDataSourceConfigurationTest {

    private static final String H2_URL = "jdbc:h2:mem:zonedb;DB_CLOSE_DELAY=-1";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ZoneDataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    DataSourceAutoConfiguration.class));

    @Test
    void defaultShouldAssembleHikari() {
        runner.withPropertyValues(
                        "zone.middleware.db.url=" + H2_URL,
                        "zone.middleware.db.username=sa",
                        "zone.middleware.db.password=")
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    assertTrue(dataSource instanceof HikariDataSource, "默认应为 HikariCP");
                    HikariDataSource hikari = (HikariDataSource) dataSource;
                    assertEquals("zone-hikari", hikari.getPoolName());
                    // LOCAL 默认泄漏检测 10s
                    assertEquals(10_000L, hikari.getLeakDetectionThreshold());

                    // 连接可用
                    Integer one = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
                    assertEquals(1, one);
                });
    }

    @Test
    void cloudModeShouldApplyCloudPresets() {
        runner.withPropertyValues(
                        "zone.middleware.db.mode=CLOUD",
                        "zone.middleware.db.url=" + H2_URL,
                        "zone.middleware.db.username=sa")
                .run(context -> {
                    HikariDataSource hikari = context.getBean(HikariDataSource.class);
                    assertEquals(15_000L, hikari.getConnectionTimeout());
                    assertEquals(0L, hikari.getLeakDetectionThreshold());
                    assertEquals(120_000L, hikari.getKeepaliveTime());
                });
    }

    @Test
    void explicitPoolSettingsShouldWin() {
        runner.withPropertyValues(
                        "zone.middleware.db.url=" + H2_URL,
                        "zone.middleware.db.username=sa",
                        "zone.middleware.db.hikari.maximum-pool-size=3",
                        "zone.middleware.db.hikari.leak-detection-threshold=2500")
                .run(context -> {
                    HikariDataSource hikari = context.getBean(HikariDataSource.class);
                    assertEquals(3, hikari.getMaximumPoolSize());
                    assertEquals(2_500L, hikari.getLeakDetectionThreshold());
                });
    }

    @Test
    void shouldAssembleDruidWhenSelected() {
        runner.withPropertyValues(
                        "zone.middleware.db.pool-type=DRUID",
                        "zone.middleware.db.url=" + H2_URL,
                        "zone.middleware.db.username=sa")
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    assertTrue(dataSource instanceof DruidDataSource, "应切换为 Druid");
                    DruidDataSource druid = (DruidDataSource) dataSource;
                    assertEquals("zone-druid", druid.getName());
                    assertEquals(5_000L, druid.getMaxWait());
                    assertEquals("SELECT 1", druid.getValidationQuery());

                    Integer one = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
                    assertEquals(1, one);
                });
    }

    @Test
    void missingUrlShouldFailWithConfigError() {
        runner.run(context -> {
            assertTrue(context.getStartupFailure() != null, "缺少 URL 应启动失败");
            Throwable cause = context.getStartupFailure();
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof ZoneBusinessException, "根因应为 ZoneBusinessException: " + cause);
            assertEquals(SystemError.CONFIG_ERROR, ((ZoneBusinessException) cause).getError());
            assertTrue(cause.getMessage().contains("url"));
        });
    }

    @Test
    void invalidPoolTypeShouldFailStartup() {
        runner.withPropertyValues(
                        "zone.middleware.db.pool-type=C3P0",
                        "zone.middleware.db.url=" + H2_URL,
                        "zone.middleware.db.username=sa")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    String message = context.getStartupFailure().getMessage();
                    assertTrue(message.contains("C3P0") || containsInCause(context.getStartupFailure(), "C3P0"),
                            "错误信息应包含非法值: " + message);
                });
    }

    private boolean containsInCause(Throwable throwable, String keyword) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(keyword)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
