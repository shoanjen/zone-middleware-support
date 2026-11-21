package com.zone.middleware.http.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * HTTP 支撑配置（前缀 zone.middleware.http）。
 */
@Data
@ConfigurationProperties(prefix = "zone.middleware.http")
public class ZoneHttpProperties {

    /** 连接超时（默认 5s） */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 单请求读超时（默认 10s） */
    private Duration readTimeout = Duration.ofSeconds(10);

    /** 重定向策略（默认 NORMAL） */
    private Redirect redirect = Redirect.NORMAL;

    /** 并发请求数上限（信号量承载，默认 64） */
    private int maxConcurrentRequests = 64;

    private final Retry retry = new Retry();

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * 获取重试配置（绑定 zone.middleware.http.retry.*）。
     *
     * @return 重试配置实例
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * 获取熔断配置（绑定 zone.middleware.http.circuit-breaker.*）。
     *
     * @return 熔断配置实例
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /** 重定向策略 */
    public enum Redirect {
        /** 不跟随重定向 */
        NONE,
        /** 跟随重定向但 https→http 除外（默认） */
        NORMAL,
        /** 始终跟随重定向 */
        ALWAYS
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

    /** 熔断配置（默认关闭） */
    @Data
    public static class CircuitBreaker {
        /** 是否开启熔断 */
        private boolean enabled = false;
        /** 连续失败阈值（达到即 OPEN） */
        private int failureThreshold = 5;
        /** OPEN 冷却时长，过后进入 HALF_OPEN 放行探测 */
        private Duration openWaitDuration = Duration.ofSeconds(30);
    }
}
