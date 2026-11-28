package com.zone.middleware.http.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 官方 HTTP 客户端键绑定（spring.http.client.*，Boot 3.4+ 键面）：连接超时、读超时与
 * 重定向策略由上游按官方键配置，本类为自建独立绑定（仿 {@code ZoneOfficialDataSourceProperties}
 * 模式，不依赖 Boot 内部工厂设置类型）。并发上限与重试无官方对应，仍由
 * {@code zone.middleware.http.*} 承载（见 {@link ZoneHttpProperties}）。
 */
public final class ZoneOfficialHttpClientProperties {

    /** 工具类禁止实例化 */
    private ZoneOfficialHttpClientProperties() {
    }

    /**
     * 官方客户端配置（绑定 spring.http.client.*）。
     */
    @Data
    @ConfigurationProperties(prefix = "spring.http.client")
    public static class Client {

        /** 连接超时（spring.http.client.connect-timeout；缺省兜底 5s） */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /** 读超时，逐请求施加（spring.http.client.read-timeout；缺省兜底 10s） */
        private Duration readTimeout = Duration.ofSeconds(10);

        /** 重定向策略（spring.http.client.redirects；缺省 follow-when-possible） */
        private Redirects redirects = Redirects.FOLLOW_WHEN_POSSIBLE;
    }

    /**
     * 重定向策略（与 Boot 官方键值对齐，仅两值；确需无条件重定向的上游自行声明 HttpClient Bean）。
     */
    public enum Redirects {
        /** 不跟随重定向（官方键值 never） */
        NEVER,
        /** 跟随重定向但 https→http 除外（官方键值 follow-when-possible） */
        FOLLOW_WHEN_POSSIBLE
    }
}
