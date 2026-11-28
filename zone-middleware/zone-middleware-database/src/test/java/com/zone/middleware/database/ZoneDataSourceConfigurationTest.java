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
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        // ApplicationContextRunner 不执行 EEP，LOCAL 预设(10s)以显式键模拟
                        "spring.datasource.hikari.leak-detection-threshold=10000")
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    assertTrue(dataSource instanceof HikariDataSource, "默认应为 HikariCP");
                    HikariDataSource hikari = (HikariDataSource) dataSource;
                    assertEquals("zone-hikari", hikari.getPoolName());
                    assertEquals(10_000L, hikari.getLeakDetectionThreshold());

                    // 连接可用
                    Integer one = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
                    assertEquals(1, one);
                });
    }

    @Test
    void officialHikariKeysShouldApply() {
        runner.withPropertyValues(
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        "spring.datasource.hikari.connection-timeout=15000",
                        "spring.datasource.hikari.keepalive-time=120000",
                        "spring.datasource.hikari.leak-detection-threshold=0")
                .run(context -> {
                    HikariDataSource hikari = context.getBean(HikariDataSource.class);
                    assertEquals(15_000L, hikari.getConnectionTimeout());
                    assertEquals(0L, hikari.getLeakDetectionThreshold());
                    assertEquals(120_000L, hikari.getKeepaliveTime());
                });
    }

    @Test
    void explicitOfficialKeysShouldApply() {
        runner.withPropertyValues(
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        "spring.datasource.hikari.maximum-pool-size=3",
                        "spring.datasource.hikari.leak-detection-threshold=2500")
                .run(context -> {
                    HikariDataSource hikari = context.getBean(HikariDataSource.class);
                    assertEquals(3, hikari.getMaximumPoolSize());
                    assertEquals(2_500L, hikari.getLeakDetectionThreshold());
                });
    }

    @Test
    void shouldAssembleDruidWhenTypeConfigured() {
        runner.withPropertyValues(
                        "spring.datasource.type=com.alibaba.druid.pool.DruidDataSource",
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        // 未配 max-wait 等项时回归 Druid 官方默认；此处显式验证官方键生效
                        "spring.datasource.druid.max-wait=5000")
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
    void druidFiltersShouldApplyWhenConfigured() {
        runner.withPropertyValues(
                        "spring.datasource.type=com.alibaba.druid.pool.DruidDataSource",
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        "spring.datasource.druid.filters=stat,slf4j")
                .run(context -> {
                    DruidDataSource druid = context.getBean(DruidDataSource.class);
                    assertEquals(2, druid.getProxyFilters().size(), "stat 与 slf4j 均应装配为 Filter 实例");
                });
    }

    @Test
    void unknownDruidFilterAliasShouldFailWithConfigError() {
        runner.withPropertyValues(
                        "spring.datasource.type=com.alibaba.druid.pool.DruidDataSource",
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        "spring.datasource.druid.filters=stat,nope")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null, "未注册 filter 别名应启动失败");
                    Throwable cause = context.getStartupFailure();
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    assertTrue(cause instanceof ZoneBusinessException, "根因应为 ZoneBusinessException: " + cause);
                    assertEquals(SystemError.CONFIG_ERROR, ((ZoneBusinessException) cause).getError());
                    assertTrue(cause.getMessage().contains("nope"), "错误信息应包含未注册别名: " + cause);
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
            assertTrue(cause.getMessage().contains("spring.datasource.url"));
        });
    }

    @Test
    void unsupportedDataSourceTypeShouldFailStartup() {
        runner.withPropertyValues(
                        "spring.datasource.type=org.example.not.PoolDataSource",
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa")
                .run(context -> {
                    // 不可解析的类名在官方 DataSourceProperties 绑定阶段即失败
                    assertTrue(context.getStartupFailure() != null, "不可解析的 type 应启动失败");
                });
    }

    @Test
    void resolvableButUnsupportedTypeShouldFailWithConfigError() {
        runner.withPropertyValues(
                        "spring.datasource.type=" + ResolvableStubDataSource.class.getName(),
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null, "不可支持的 type 应启动失败");
                    String message = context.getStartupFailure().getMessage();
                    assertTrue(message.contains("spring.datasource.type") || containsInCause(context.getStartupFailure(), "spring.datasource.type"),
                            "错误信息应包含键名: " + message);
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

    /** 可解析但不受支持的 DataSource 实现（用于触发装配期 CONFIG_ERROR 分支） */
    static class ResolvableStubDataSource extends HikariDataSource {
    }
}
