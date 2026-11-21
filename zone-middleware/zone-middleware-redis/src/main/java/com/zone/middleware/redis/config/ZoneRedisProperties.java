package com.zone.middleware.redis.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis 支撑配置（前缀 zone.middleware.redis）。
 *
 * <p>部署模式：{@code mode = LOCAL | CLOUD}（默认 LOCAL）。CLOUD 模式自动套用更保守的
 * 预设值（更长超时、开启 TCP 保活），所有预设均可被显式配置逐项覆盖。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zone.middleware.redis")
public class ZoneRedisProperties {

    /** 本地模式默认连接超时 */
    public static final Duration LOCAL_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    /** 本地模式默认命令超时 */
    public static final Duration LOCAL_COMMAND_TIMEOUT = Duration.ofSeconds(3);
    /** 云端模式默认连接超时 */
    public static final Duration CLOUD_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 云端模式默认命令超时 */
    public static final Duration CLOUD_COMMAND_TIMEOUT = Duration.ofSeconds(10);

    /** 部署模式：LOCAL / CLOUD */
    private String mode = "LOCAL";

    private String host = "127.0.0.1";
    private int port = 6379;
    private String password;
    private int database = 0;
    /** 是否启用 TLS（云端 TLS 由上游按需开启） */
    private boolean ssl = false;
    /** 连接超时；null = 未配置，按模式取预设 */
    private Duration connectTimeout;
    /** 命令超时；null = 未配置，按模式取预设 */
    private Duration commandTimeout;
    /** TCP 保活；null = 未配置，按模式取预设 */
    private Boolean keepAlive;

    private final Pool pool = new Pool();

    /**
     * 获取连接池配置（绑定 zone.middleware.redis.pool.*）。
     *
     * @return 连接池配置实例
     */
    public Pool getPool() {
        return pool;
    }

    /**
     * 部署模式。
     */
    public enum DeployMode {
        /** 本地部署：宽松预设，便于开发排查 */
        LOCAL,
        /** 云端部署：保守预设（更长超时、TCP 保活） */
        CLOUD
    }

    /** 连接池配置 */
    @Data
    public static class Pool {
        /** 最大连接数 */
        private int maxActive = 8;
        /** 最大空闲连接数 */
        private int maxIdle = 8;
        /** 最小空闲连接数 */
        private int minIdle = 0;
        /** 获取连接最大等待 */
        private Duration maxWait = Duration.ofSeconds(2);
    }

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
                    "无效的 zone.middleware.redis.mode 配置值: " + mode + "，合法取值为 LOCAL / CLOUD");
        }
    }

    /**
     * 获取生效连接超时：显式配置 > 模式预设。
     *
     * @return 生效连接超时
     */
    public Duration resolvedConnectTimeout() {
        if (connectTimeout != null) {
            return connectTimeout;
        }
        return parsedMode() == DeployMode.CLOUD ? CLOUD_CONNECT_TIMEOUT : LOCAL_CONNECT_TIMEOUT;
    }

    /**
     * 获取生效命令超时：显式配置 > 模式预设。
     *
     * @return 生效命令超时
     */
    public Duration resolvedCommandTimeout() {
        if (commandTimeout != null) {
            return commandTimeout;
        }
        return parsedMode() == DeployMode.CLOUD ? CLOUD_COMMAND_TIMEOUT : LOCAL_COMMAND_TIMEOUT;
    }

    /**
     * 获取生效 TCP 保活开关：显式配置 > 模式预设（CLOUD 默认开）。
     *
     * @return 是否开启 TCP 保活
     */
    public boolean resolvedKeepAlive() {
        if (keepAlive != null) {
            return keepAlive;
        }
        return parsedMode() == DeployMode.CLOUD;
    }
}
