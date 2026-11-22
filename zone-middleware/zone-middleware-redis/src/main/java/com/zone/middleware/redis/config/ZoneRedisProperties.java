package com.zone.middleware.redis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 支撑配置（前缀 zone.middleware.redis；仅保留行为键）。
 *
 * <p>连接配置由上游经 Spring Boot 官方键 spring.data.redis.* 提供，连接工厂由官方
 * RedisAutoConfiguration 装配；本键面只承载部署模式 {@code mode = LOCAL | CLOUD}（默认 LOCAL）。
 * 模式预设（LOCAL 宽松/ CLOUD 保守超时与 TCP 保活）经 ZoneRedisPresetEnvironmentPostProcessor
 * 以官方键注入，显式配置总是优先；TCP 保活无官方键，经 LettuceClientOptionsBuilderCustomizer
 * 按模式应用（LOCAL 关 / CLOUD 开）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zone.middleware.redis")
public class ZoneRedisProperties {

    /** 部署模式：LOCAL / CLOUD */
    private String mode = "LOCAL";

    /**
     * 部署模式。
     */
    public enum DeployMode {
        /** 本地部署：宽松预设，便于开发排查 */
        LOCAL,
        /** 云端部署：保守预设（更长超时、TCP 保活） */
        CLOUD
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
}
