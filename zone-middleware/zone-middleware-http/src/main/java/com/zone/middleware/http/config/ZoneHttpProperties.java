package com.zone.middleware.http.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * HTTP 支撑配置（前缀 zone.middleware.http，仅保留行为键）。
 *
 * <p>连接超时、读超时与重定向策略由上游按 Spring Boot 官方键
 * {@code spring.http.client.connect-timeout / read-timeout / redirects} 配置
 * （绑定见 {@link ZoneOfficialHttpClientProperties}），本类不再承载；
 * 并发上限与重试无官方对应键，保留在本前缀下。</p>
 */
@Data
@ConfigurationProperties(prefix = "zone.middleware.http")
public class ZoneHttpProperties {

    /** 并发请求数上限（信号量承载，默认 64） */
    private int maxConcurrentRequests = 64;

    private final Retry retry = new Retry();

    /**
     * 获取重试配置（绑定 zone.middleware.http.retry.*）。
     *
     * @return 重试配置实例
     */
    public Retry getRetry() {
        return retry;
    }

    /** 失败重试配置（默认关闭） */
    @Data
    public static class Retry {
        /** 是否开启重试 */
        private boolean enabled = false;
        /** 最大尝试次数（含首次） */
        private int maxAttempts = 3;
        /** 退避初始值 */
        private Duration initialBackoff = Duration.ofMillis(500);
        /** 退避倍数 */
        private double backoffMultiplier = 2.0;
        /** 退避上限 */
        private Duration maxBackoff = Duration.ofSeconds(10);
        /** 可重试状态码 */
        private Set<Integer> retryableStatuses = new LinkedHashSet<>(Set.of(429, 502, 503, 504));
        /** 网络异常是否可重试 */
        private boolean retryOnIoError = true;
    }
}
