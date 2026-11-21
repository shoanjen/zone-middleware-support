package com.zone.middleware.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Zone 安全支撑配置项（{@code zone.middleware.security.*}）。
 *
 * <p>配置示例：</p>
 * <pre>{@code
 * zone:
 *   middleware:
 *     security:
 *       sign:
 *         enabled: true
 *         secret: your-sign-secret
 *         timestamp-window: 5m
 *       token:
 *         ttl: 7d
 *         renew-sliding: true
 *         multi-device: true
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "zone.middleware.security")
public class ZoneSecurityProperties {

    /** 接口签名校验配置 */
    private final Sign sign = new Sign();

    /** 登录令牌配置 */
    private final Token token = new Token();

    /**
     * 签名校验配置项（{@code zone.middleware.security.sign.*}）。
     */
    @Data
    public static class Sign {

        /** 是否启用签名校验拦截器（纯令牌场景可关闭；关闭后 @ZoneSignCheck 端点不拦截） */
        private boolean enabled = true;

        /** 双端约定的签名密钥（web 与客户端共用同一个）；启用签名校验时必须配置 */
        private String secret;

        /** 防重放时间窗：请求 timestamp 与服务器时间允许的最大偏差 */
        private Duration timestampWindow = Duration.ofMinutes(5);
    }

    /**
     * 登录令牌配置项（{@code zone.middleware.security.token.*}）。
     */
    @Data
    public static class Token {

        /** 令牌默认生存期（签发与滑动续期共用） */
        private Duration ttl = Duration.ofDays(7);

        /** 是否滑动续期：校验通过时生存期重置为完整 TTL（关闭后自签发起固定） */
        private boolean renewSliding = true;

        /** 多端并存：true 时同一用户重复登录并存多枚令牌；false 时单端互斥（新登录踢掉全部旧令牌） */
        private boolean multiDevice = true;
    }
}
