package com.zone.middleware.database.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库支撑配置（前缀 zone.middleware.db）。
 *
 * <p>部署模式 {@code mode = LOCAL | CLOUD}（默认 LOCAL）：LOCAL 默认开启连接泄漏检测便于开发排查；
 * CLOUD 套用更保守的预设（更长获取超时、连接保活、关闭泄漏检测）。连接池类型
 * {@code pool-type = HIKARI | DRUID}（默认 HIKARI）。预设均可被显式配置逐项覆盖。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zone.middleware.db")
public class ZoneDatabaseProperties {

    /** 部署模式：LOCAL / CLOUD */
    private String mode = "LOCAL";
    /** 连接池类型：HIKARI / DRUID */
    private String poolType = "HIKARI";

    private String url;
    private String username;
    private String password;
    private String driverClassName;

    private final Hikari hikari = new Hikari();
    private final Druid druid = new Druid();
    private final Mybatis mybatis = new Mybatis();
    private final Flyway flyway = new Flyway();

    /**
     * 获取 HikariCP 连接池配置（绑定 zone.middleware.db.hikari.*）。
     *
     * @return Hikari 配置实例
     */
    public Hikari getHikari() {
        return hikari;
    }

    /**
     * 获取 Druid 连接池配置（绑定 zone.middleware.db.druid.*）。
     *
     * @return Druid 配置实例
     */
    public Druid getDruid() {
        return druid;
    }

    /**
     * 获取 MyBatis-Plus 整合配置（绑定 zone.middleware.db.mybatis.*）。
     *
     * @return MyBatis 配置实例
     */
    public Mybatis getMybatis() {
        return mybatis;
    }

    /**
     * 获取 Flyway 迁移配置（绑定 zone.middleware.db.flyway.*）。
     *
     * @return Flyway 配置实例
     */
    public Flyway getFlyway() {
        return flyway;
    }

    /**
     * 部署模式。
     */
    public enum DeployMode {
        /** 本地部署：默认开启泄漏检测便于开发排查 */
        LOCAL,
        /** 云端部署：保守预设（更长超时、连接保活、关闭泄漏检测） */
        CLOUD
    }

    /**
     * 连接池类型。
     */
    public enum PoolType {
        /** HikariCP 连接池（默认） */
        HIKARI,
        /** Druid 连接池 */
        DRUID
    }

    /** HikariCP 连接池配置（null = 未配置，按模式取预设） */
    @Data
    public static class Hikari {
        /** 连接获取超时（毫秒） */
        private Long connectionTimeout;
        /** 连接池最大连接数 */
        private Integer maximumPoolSize;
        /** 最小空闲连接数 */
        private Integer minimumIdle;
        /** 连接泄漏检测阈值（毫秒），0 为关闭 */
        private Long leakDetectionThreshold;
        /** 连接保活间隔（毫秒），0 为关闭 */
        private Long keepaliveTime;
    }

    /** Druid 连接池配置（null = 未配置，按模式取预设） */
    @Data
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
    }

    /** MyBatis-Plus 整合配置 */
    @Data
    public static class Mybatis {
        /** 是否默认装配分页插件 */
        private boolean paginationEnabled = true;
        /** Mapper 扫描包（可选；未配置时不强制扫描） */
        private String mapperPackage;
    }

    /** Flyway 迁移配置 */
    @Data
    public static class Flyway {
        /** 是否启用迁移 */
        private boolean enabled = true;
        /** 迁移脚本路径（逗号分隔），默认 classpath:db/migration */
        private String locations;
        /** 是否允许对已有库基线化 */
        private Boolean baselineOnMigrate;
    }

    // ==================== 解析与预设 ====================

    /**
     * 解析部署模式。
     *
     * @return 部署模式枚举
     * @throws IllegalArgumentException mode 为非法值（提示包含非法值与合法取值）
     */
    public DeployMode parsedMode() {
        try {
            return DeployMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "无效的 zone.middleware.db.mode 配置值: " + mode + "，合法取值为 LOCAL / CLOUD");
        }
    }

    /**
     * 解析连接池类型。
     *
     * @return 连接池类型枚举
     * @throws IllegalArgumentException pool-type 为非法值（提示包含非法值与合法取值）
     */
    public PoolType parsedPoolType() {
        try {
            return PoolType.valueOf(poolType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "无效的 zone.middleware.db.pool-type 配置值: " + poolType + "，合法取值为 HIKARI / DRUID");
        }
    }

    /**
     * 获取生效 Hikari 连接获取超时：显式配置 > 模式预设（LOCAL 5s / CLOUD 15s）。
     *
     * @return 生效超时（毫秒）
     */
    public long resolvedHikariConnectionTimeout() {
        if (hikari.connectionTimeout != null) {
            return hikari.connectionTimeout;
        }
        return parsedMode() == DeployMode.CLOUD ? 15_000L : 5_000L;
    }

    /**
     * 获取生效 Hikari 泄漏检测阈值：显式配置 > 模式预设（LOCAL 10s / CLOUD 关闭）。
     *
     * @return 生效阈值（毫秒，0 表示关闭）
     */
    public long resolvedHikariLeakDetection() {
        if (hikari.leakDetectionThreshold != null) {
            return hikari.leakDetectionThreshold;
        }
        return parsedMode() == DeployMode.LOCAL ? 10_000L : 0L;
    }

    /**
     * 获取生效 Hikari 保活间隔：显式配置 > 模式预设（LOCAL 关闭 / CLOUD 120s）。
     *
     * @return 生效间隔（毫秒，0 表示关闭）
     */
    public long resolvedHikariKeepalive() {
        if (hikari.keepaliveTime != null) {
            return hikari.keepaliveTime;
        }
        return parsedMode() == DeployMode.CLOUD ? 120_000L : 0L;
    }

    /**
     * 获取生效 Druid 获取等待：显式配置 > 模式预设（LOCAL 5s / CLOUD 15s）。
     *
     * @return 生效等待（毫秒）
     */
    public long resolvedDruidMaxWait() {
        if (druid.maxWait != null) {
            return druid.maxWait;
        }
        return parsedMode() == DeployMode.CLOUD ? 15_000L : 5_000L;
    }

    /**
     * 获取生效 Druid 保活开关：显式配置 > 模式预设（CLOUD 开）。
     *
     * @return 是否开启保活
     */
    public boolean resolvedDruidKeepAlive() {
        if (druid.keepAlive != null) {
            return druid.keepAlive;
        }
        return parsedMode() == DeployMode.CLOUD;
    }

    /**
     * 获取生效 Druid 初始连接数：显式配置 > 默认值 2。
     *
     * @return 生效初始连接数
     */
    public int resolvedDruidInitialSize() {
        return druid.initialSize != null ? druid.initialSize : 2;
    }

    /**
     * 获取生效 Druid 最小空闲连接数：显式配置 > 默认值 2。
     *
     * @return 生效最小空闲数
     */
    public int resolvedDruidMinIdle() {
        return druid.minIdle != null ? druid.minIdle : 2;
    }

    /**
     * 获取生效 Druid 最大活跃连接数：显式配置 > 默认值 8。
     *
     * @return 生效最大活跃数
     */
    public int resolvedDruidMaxActive() {
        return druid.maxActive != null ? druid.maxActive : 8;
    }
}
