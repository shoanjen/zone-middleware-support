package com.zone.middleware.database.config;

import com.alibaba.druid.filter.FilterManager;
import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.database.config.ZoneOfficialDataSourceProperties.Druid;
import com.zone.middleware.database.config.ZoneOfficialDataSourceProperties.Hikari;
import com.zone.middleware.log.ZoneLog;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 数据源自动装配：按 Spring Boot 官方键（spring.datasource.*）构建 HikariCP 或 Druid 数据源
 * （手工装配，不依赖各家 starter 自动配置）。连接信息取自官方 {@link DataSourceProperties}，
 * 池参数绑定官方前缀（spring.datasource.hikari.* / spring.datasource.druid.*），
 * 连接池类型由 spring.datasource.type 选择（缺省 HikariCP，Druid 填全限定类名），
 * 未配置项回归官方默认值。事务管理由 Spring Boot 自动配置基于本数据源生效。
 *
 * <p>注意：必须排在 Spring/MyBatis-Plus 的相关自动配置之前（按类名字母序 com.zone 恰好靠后，
 * 故显式声明 Before），否则它们的 DataSource 缺失条件会先被评估而回退/跳过。</p>
 */
@AutoConfiguration
@AutoConfigureBefore({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class})
@EnableConfigurationProperties({
        DataSourceProperties.class,
        ZoneOfficialDataSourceProperties.Hikari.class,
        ZoneOfficialDataSourceProperties.Druid.class})
public class ZoneDataSourceAutoConfiguration {

    private static final String VALIDATION_QUERY = "SELECT 1";
    private static final String DRUID_FILTER_ALIAS_KEY_PREFIX = "druid.filters.";
    private static final String DRUID_CLASS_NAME = "com.alibaba.druid.pool.DruidDataSource";

    /**
     * 构建 zone 数据源（HikariCP / Druid 按 spring.datasource.type 切换）。
     *
     * @param dataSourceProperties 官方数据源配置（spring.datasource.*）
     * @param hikari               HikariCP 池参数（spring.datasource.hikari.*）
     * @param druid                Druid 池参数（spring.datasource.druid.*）
     * @return 按连接池类型装配的 DataSource
     * @throws ZoneBusinessException url 未配置（CONFIG_ERROR）或 type 非法
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource zoneDataSource(DataSourceProperties dataSourceProperties,
                                     Hikari hikari,
                                     Druid druid) {
        if (dataSourceProperties.getUrl() == null || dataSourceProperties.getUrl().trim().isEmpty()) {
            throw new ZoneBusinessException(SystemError.CONFIG_ERROR,
                    "spring.datasource.url 未配置，请在上游应用提供数据库连接 URL");
        }
        DataSource dataSource;
        Class<? extends DataSource> type = dataSourceProperties.getType();
        if (type == null || HikariDataSource.class.equals(type)) {
            dataSource = buildHikari(dataSourceProperties, hikari);
        } else if (DruidDataSource.class.equals(type)) {
            dataSource = buildDruid(dataSourceProperties, druid);
        } else {
            throw new ZoneBusinessException(SystemError.CONFIG_ERROR,
                    "无效的 spring.datasource.type 配置值: " + type.getName()
                            + "，合法取值为 HikariCP（缺省）或 Druid（" + DRUID_CLASS_NAME + "）");
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("poolType", dataSource.getClass().getSimpleName());
        info.put("url", dataSourceProperties.getUrl());
        ZoneLog.info("ZoneDataSourceAutoConfiguration", "zoneDataSource",
                "zone-datasource init: {}", info);
        return dataSource;
    }

    /**
     * 构建 HikariCP 数据源。
     *
     * @param dataSourceProperties 官方连接信息
     * @param hikari               官方前缀 HikariCP 池参数
     * @return 已完成参数设置的 HikariDataSource
     */
    private HikariDataSource buildHikari(DataSourceProperties dataSourceProperties, Hikari hikari) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(dataSourceProperties.getUrl());
        dataSource.setUsername(dataSourceProperties.getUsername());
        dataSource.setPassword(dataSourceProperties.getPassword());
        if (dataSourceProperties.getDriverClassName() != null
                && !dataSourceProperties.getDriverClassName().isEmpty()) {
            dataSource.setDriverClassName(dataSourceProperties.getDriverClassName());
        }
        dataSource.setPoolName("zone-hikari");
        if (hikari.getConnectionTimeout() != null) {
            dataSource.setConnectionTimeout(hikari.getConnectionTimeout());
        }
        if (hikari.getLeakDetectionThreshold() != null) {
            dataSource.setLeakDetectionThreshold(hikari.getLeakDetectionThreshold());
        }
        if (hikari.getKeepaliveTime() != null && hikari.getKeepaliveTime() > 0) {
            dataSource.setKeepaliveTime(hikari.getKeepaliveTime());
        }
        if (hikari.getMaximumPoolSize() != null) {
            dataSource.setMaximumPoolSize(hikari.getMaximumPoolSize());
        }
        if (hikari.getMinimumIdle() != null) {
            dataSource.setMinimumIdle(hikari.getMinimumIdle());
        }
        return dataSource;
    }

    /**
     * 构建 Druid 数据源（含 validationQuery 与可选 filter 别名）。
     *
     * @param dataSourceProperties 官方连接信息
     * @param druid                官方前缀 Druid 池参数
     * @return 已完成参数设置的 DruidDataSource
     */
    private DruidDataSource buildDruid(DataSourceProperties dataSourceProperties, Druid druid) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(dataSourceProperties.getUrl());
        dataSource.setUsername(dataSourceProperties.getUsername());
        dataSource.setPassword(dataSourceProperties.getPassword());
        if (dataSourceProperties.getDriverClassName() != null
                && !dataSourceProperties.getDriverClassName().isEmpty()) {
            dataSource.setDriverClassName(dataSourceProperties.getDriverClassName());
        }
        dataSource.setName("zone-druid");
        if (druid.getInitialSize() != null) {
            dataSource.setInitialSize(druid.getInitialSize());
        }
        if (druid.getMinIdle() != null) {
            dataSource.setMinIdle(druid.getMinIdle());
        }
        if (druid.getMaxActive() != null) {
            dataSource.setMaxActive(druid.getMaxActive());
        }
        if (druid.getMaxWait() != null) {
            dataSource.setMaxWait(druid.getMaxWait());
        }
        if (druid.getKeepAlive() != null) {
            dataSource.setKeepAlive(druid.getKeepAlive());
        }
        dataSource.setTestWhileIdle(true);
        dataSource.setValidationQuery(VALIDATION_QUERY);
        applyDruidFilters(dataSource, druid.getFilters());
        return dataSource;
    }

    /**
     * 应用 Druid filter 别名配置（逗号分隔，如 stat,wall,slf4j）。
     *
     * <p>Druid 对未注册别名仅打日志静默跳过，这里先行校验别名合法性并快速失败，
     * 避免上游配错后监控/防护能力静默丢失。</p>
     *
     * @param dataSource 待装配的 Druid 数据源
     * @param filters    spring.datasource.druid.filters 配置值
     * @throws ZoneBusinessException 存在未注册别名（CONFIG_ERROR）或 filter 装配失败
     */
    private void applyDruidFilters(DruidDataSource dataSource, String filters) {
        if (filters == null || filters.trim().isEmpty()) {
            return;
        }
        Properties filterRegistry = loadDruidFilterRegistry();
        for (String alias : filters.split(",")) {
            String trimmedAlias = alias.trim();
            if (!trimmedAlias.isEmpty()
                    && filterRegistry.getProperty(DRUID_FILTER_ALIAS_KEY_PREFIX + trimmedAlias) == null) {
                throw new ZoneBusinessException(SystemError.CONFIG_ERROR,
                        "无效的 spring.datasource.druid.filters 配置值: " + trimmedAlias
                                + "，合法取值为 Druid 注册的 filter 别名（stat / wall / slf4j 等）");
            }
        }
        try {
            dataSource.setFilters(filters);
        } catch (SQLException e) {
            throw new ZoneBusinessException(SystemError.CONFIG_ERROR,
                    "spring.datasource.druid.filters 应用失败: " + filters, e);
        }
    }

    /**
     * 读取 Druid 内置 filter 别名注册表（META-INF/druid-filter.properties）。
     *
     * @return 别名注册表（键形如 druid.filters.&lt;alias&gt;）
     * @throws ZoneBusinessException 注册表读取失败（CONFIG_ERROR）
     */
    private Properties loadDruidFilterRegistry() {
        try {
            return FilterManager.loadFilterConfig();
        } catch (IOException e) {
            throw new ZoneBusinessException(SystemError.CONFIG_ERROR, "读取 Druid filter 别名注册表失败", e);
        }
    }
}
