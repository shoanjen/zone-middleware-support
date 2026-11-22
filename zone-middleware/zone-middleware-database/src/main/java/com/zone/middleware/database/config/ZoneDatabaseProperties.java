package com.zone.middleware.database.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库支撑配置（仅保留行为键；连接与池参数由上游经 Spring Boot 官方键 spring.datasource.* 提供）。
 *
 * <p>部署模式 {@code mode = LOCAL | CLOUD}（默认 LOCAL）：LOCAL 默认开启连接泄漏检测便于开发排查；
 * CLOUD 套用更保守的预设（更长获取超时、连接保活、关闭泄漏检测），预设经
 * {@link ZoneDbPresetEnvironmentPostProcessor} 以官方键注入，显式配置总是优先。
 * MyBatis-Plus 整合段 {@code mybatis.*} 为本库行为件（官方 MP starter 无此能力）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zone.middleware.db")
public class ZoneDatabaseProperties {

    /** 部署模式：LOCAL / CLOUD */
    private String mode = "LOCAL";

    private final Mybatis mybatis = new Mybatis();

    /**
     * 获取 MyBatis-Plus 整合配置（绑定 zone.middleware.db.mybatis.*）。
     *
     * @return MyBatis 配置实例
     */
    public Mybatis getMybatis() {
        return mybatis;
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

    /** MyBatis-Plus 整合配置 */
    @Data
    public static class Mybatis {
        /** 是否默认装配分页插件 */
        private boolean paginationEnabled = true;
        /** Mapper 扫描包（可选；未配置时不强制扫描） */
        private String mapperPackage;
    }

    // ==================== 解析 ====================

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
}
