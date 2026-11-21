package com.zone.middleware.database.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.log.ZoneLog;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源自动装配：按 zone.middleware.db.* 构建 HikariCP 或 Druid 数据源（手工装配，不依赖各家 starter 自动配置）。
 * 事务管理由 Spring Boot 自动配置基于本数据源生效。
 *
 * <p>注意：必须排在 Spring/MyBatis-Plus 的相关自动配置之前（按类名字母序 com.zone 恰好靠后，
 * 故显式声明 Before），否则它们的 DataSource 缺失条件会先被评估而回退/跳过。</p>
 */
@AutoConfiguration
@AutoConfigureBefore({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class})
@EnableConfigurationProperties(ZoneDatabaseProperties.class)
public class ZoneDataSourceAutoConfiguration {

    private static final String VALIDATION_QUERY = "SELECT 1";

    /**
     * 构建 zone 数据源（HikariCP / Druid 按配置切换）。
     *
     * @param properties 绑定的 zone.middleware.db.* 配置
     * @return 按连接池类型装配的 DataSource
     * @throws ZoneBusinessException url 未配置（CONFIG_ERROR）或 mode/pool-type 非法
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource zoneDataSource(ZoneDatabaseProperties properties) {
        if (properties.getUrl() == null || properties.getUrl().trim().isEmpty()) {
            throw new ZoneBusinessException(SystemError.CONFIG_ERROR,
                    "zone.middleware.db.url 未配置，请在上游应用提供数据库连接 URL");
        }
        ZoneDatabaseProperties.DeployMode mode = properties.parsedMode();
        ZoneDatabaseProperties.PoolType poolType = properties.parsedPoolType();

        DataSource dataSource = poolType == ZoneDatabaseProperties.PoolType.DRUID
                ? buildDruid(properties)
                : buildHikari(properties);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", mode);
        info.put("poolType", poolType);
        info.put("url", properties.getUrl());
        ZoneLog.info("ZoneDataSourceAutoConfiguration", "zoneDataSource",
                "zone-datasource init: {}", info);
        return dataSource;
    }

    /**
     * 构建 HikariCP 数据源（套用模式预设）。
     *
     * @param properties zone 数据库配置
     * @return 已完成参数设置的 HikariDataSource
     */
    private HikariDataSource buildHikari(ZoneDatabaseProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        if (properties.getDriverClassName() != null && !properties.getDriverClassName().isEmpty()) {
            dataSource.setDriverClassName(properties.getDriverClassName());
        }
        dataSource.setPoolName("zone-hikari");
        dataSource.setConnectionTimeout(properties.resolvedHikariConnectionTimeout());
        dataSource.setLeakDetectionThreshold(properties.resolvedHikariLeakDetection());
        long keepalive = properties.resolvedHikariKeepalive();
        if (keepalive > 0) {
            dataSource.setKeepaliveTime(keepalive);
        }
        if (properties.getHikari().getMaximumPoolSize() != null) {
            dataSource.setMaximumPoolSize(properties.getHikari().getMaximumPoolSize());
        }
        if (properties.getHikari().getMinimumIdle() != null) {
            dataSource.setMinimumIdle(properties.getHikari().getMinimumIdle());
        }
        return dataSource;
    }

    /**
     * 构建 Druid 数据源（套用模式预设，含 validationQuery）。
     *
     * @param properties zone 数据库配置
     * @return 已完成参数设置的 DruidDataSource
     */
    private DruidDataSource buildDruid(ZoneDatabaseProperties properties) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        if (properties.getDriverClassName() != null && !properties.getDriverClassName().isEmpty()) {
            dataSource.setDriverClassName(properties.getDriverClassName());
        }
        dataSource.setName("zone-druid");
        dataSource.setInitialSize(properties.resolvedDruidInitialSize());
        dataSource.setMinIdle(properties.resolvedDruidMinIdle());
        dataSource.setMaxActive(properties.resolvedDruidMaxActive());
        dataSource.setMaxWait(properties.resolvedDruidMaxWait());
        dataSource.setKeepAlive(properties.resolvedDruidKeepAlive());
        dataSource.setTestWhileIdle(true);
        dataSource.setValidationQuery(VALIDATION_QUERY);
        return dataSource;
    }
}
