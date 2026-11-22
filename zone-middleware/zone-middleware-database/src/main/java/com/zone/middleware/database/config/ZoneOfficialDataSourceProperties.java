package com.zone.middleware.database.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Spring Boot 官方键绑定类：承载本库装配数据源所需的官方配置面（独立绑定，不与 Boot 内部
 * {@code DataSourceProperties} 子绑定共享实例）。
 *
 * <p>绑定 {@code spring.datasource.hikari.*}（HikariCP 池参数，字段名对齐官方
 * {@code HikariProperties} 语义）与 {@code spring.datasource.druid.*}（Druid 池参数与
 * filter 别名，键名对齐 druid-spring-boot 生态约定）。</p>
 */
public final class ZoneOfficialDataSourceProperties {

    private ZoneOfficialDataSourceProperties() {
    }

    /** HikariCP 池参数（绑定 spring.datasource.hikari.*；null = 未配置，按模式预设/官方默认） */
    @Data
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public static class Hikari {
        /** 连接获取超时（官方键 spring.datasource.hikari.connection-timeout，毫秒） */
        private Long connectionTimeout;
        /** 最大连接数 */
        private Integer maximumPoolSize;
        /** 最小空闲连接数 */
        private Integer minimumIdle;
        /** 连接泄漏检测阈值（毫秒），0 为关闭 */
        private Long leakDetectionThreshold;
        /** 连接保活间隔（毫秒），0 为关闭 */
        private Long keepaliveTime;
    }

    /** Druid 池参数与 filter（绑定 spring.datasource.druid.*；null = 未配置，按模式预设） */
    @Data
    @ConfigurationProperties(prefix = "spring.datasource.druid")
    public static class Druid {
        /** 初始连接数 */
        private Integer initialSize;
        /** 最小空闲连接数 */
        private Integer minIdle;
        /** 最大活跃连接数 */
        private Integer maxActive;
        /** 获取连接最大等待（毫秒） */
        private Long maxWait;
        /** 是否开启连接保活 */
        private Boolean keepAlive;
        /** filter 别名列表（逗号分隔，如 stat,wall,slf4j）；存在未注册别名时启动失败 */
        private String filters;
    }
}
