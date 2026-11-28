package com.zone.middleware.database;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.database.config.ZoneDruidFilterValidationConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源装配测试（装配所有权回归官方后的三路验证）：
 * HikariCP 由 Boot 原生 DataSourceAutoConfiguration 装配；Druid 由官方 starter
 * DruidDataSourceAutoConfigure 装配；filter 别名由 ZoneDruidFilterValidationConfiguration
 * 在数据源装配完成后快速失败校验。
 */
class ZoneDataSourceConfigurationTest {

    private static final String H2_URL = "jdbc:h2:mem:zonedb;DB_CLOSE_DELAY=-1";

    /** Boot 原生 Hikari 路径：仅注册官方数据源自动配置（测试类路径虽含 druid starter，不注册即不生效） */
    private final ApplicationContextRunner hikariRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class));

    /** 官方 starter Druid 路径：starter 自动配置优先于 DataSourceAutoConfiguration（@AutoConfigureBefore） */
    private final ApplicationContextRunner druidRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ZoneDruidFilterValidationConfiguration.class,
                    DruidDataSourceAutoConfigure.class,
                    DataSourceAutoConfiguration.class));

    @Test
    void bootNativeShouldAssembleHikariAndBindOfficialKeys() {
        hikariRunner.withPropertyValues(
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.datasource.hikari.maximum-pool-size=3",
                        "spring.datasource.hikari.leak-detection-threshold=2500")
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    assertTrue(dataSource instanceof HikariDataSource, "缺省应为 Boot 原生 HikariCP");
                    HikariDataSource hikari = (HikariDataSource) dataSource;
                    assertEquals(3, hikari.getMaximumPoolSize(), "官方键 hikari.* 应全量绑定");
                    assertEquals(2_500L, hikari.getLeakDetectionThreshold());

                    Integer one = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
                    assertEquals(1, one);
                });
    }

    @Test
    void hikariMissingUrlShouldFollowBootNativeSemantics() {
        // Boot 原生语义：url 缺失时 DataSourceProperties 按类路径检测自动填充内嵌 H2 内存库
        // （仍以 HikariDataSource 形态承载）；无内嵌驱动场景下 Hikari 首连接报错，属 Boot 官方行为
        hikariRunner.run(context -> {
            assertTrue(context.getStartupFailure() == null,
                    "Boot 原生语义：url 缺失不再由本库阻断启动");
            DataSource dataSource = context.getBean(DataSource.class);
            assertTrue(dataSource instanceof HikariDataSource,
                    "url 缺失时仍应装配 HikariCP（由 Boot 自动填充内嵌库地址）");
            String jdbcUrl = ((HikariDataSource) dataSource).getJdbcUrl();
            assertNotNull(jdbcUrl, "Boot 应自动填充内嵌库 url");
            assertTrue(jdbcUrl.startsWith("jdbc:h2:mem:"), "自动填充地址应为内嵌 H2: " + jdbcUrl);
            Integer one = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
            assertEquals(1, one);
        });
    }

    @Test
    void druidStarterShouldAssembleDruidAndBindOfficialKeys() {
        druidRunner.withPropertyValues(
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        // 前缀 spring.datasource.druid.* 与历史配置兼容，由 starter 绑定生效
                        "spring.datasource.druid.max-wait=5000",
                        "spring.datasource.druid.initial-size=1")
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    assertTrue(dataSource instanceof DruidDataSource, "声明 starter 应装配为 Druid");
                    DruidDataSource druid = (DruidDataSource) dataSource;
                    assertEquals(5_000L, druid.getMaxWait(), "官方键 druid.* 应由 starter 绑定");
                    assertEquals(1, druid.getInitialSize());

                    Integer one = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
                    assertEquals(1, one);
                });
    }

    @Test
    void druidFiltersShouldApplyWhenConfigured() {
        druidRunner.withPropertyValues(
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
        druidRunner.withPropertyValues(
                        "spring.datasource.url=" + H2_URL,
                        "spring.datasource.username=sa",
                        "spring.datasource.druid.filters=stat,nope")
                .run(context -> {
                    assertNotNull(context.getStartupFailure(), "未注册 filter 别名应在装配后快速失败");
                    Throwable cause = context.getStartupFailure();
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    assertTrue(cause instanceof ZoneBusinessException, "根因应为 ZoneBusinessException: " + cause);
                    assertEquals(SystemError.CONFIG_ERROR, ((ZoneBusinessException) cause).getError());
                    assertTrue(cause.getMessage().contains("nope"), "错误信息应包含未注册别名: " + cause);
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
