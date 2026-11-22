package com.zone.middleware.redis.config;

import com.zone.middleware.log.ZoneLog;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * TCP 保活按部署模式应用（LOCAL 关 / CLOUD 开）——Boot 3.1–3.3 legacy 路径。
 *
 * <p>旧版 Boot 无 {@code LettuceClientOptionsBuilderCustomizer}（仅 3.4+），只能经
 * {@link LettuceClientConfigurationBuilderCustomizer} 整体替换 ClientOptions。官方
 * （Boot 3.3.x 实证）非 SSL 独立装配的 ClientOptions 构成为：纯 builder +
 * socketOptions(connectTimeout) + timeoutOptions(enabled)，此处按此复刻并附加 keepAlive，
 * 显式回填官方 connectTimeout 保证语义不变。</p>
 *
 * <p>已知边界：官方键开启 SSL（spring.data.redis.ssl.enabled=true）时本定制器直接跳过
 * （避免整体替换丢失 Boot 构建的 SslOptions），即旧版 Boot + SSL 场景 keepAlive 不生效；
 * Boot 3.4+ 无此限制（见 ZoneRedisKeepAliveAutoConfiguration）。</p>
 */
@AutoConfiguration
@ConditionalOnClass(LettuceClientConfigurationBuilderCustomizer.class)
@ConditionalOnMissingClass(
        "org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer")
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties({ZoneRedisProperties.class, RedisProperties.class})
public class ZoneRedisLegacyKeepAliveConfiguration {

    /**
     * 按部署模式应用 Lettuce TCP 保活（Boot 3.1–3.3，非 SSL 独立路径）。
     *
     * @param zoneProperties  zone 部署模式
     * @param redisProperties 官方连接配置
     * @return 客户端配置定制器
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer zoneLettuceLegacyKeepAliveCustomizer(
            ZoneRedisProperties zoneProperties, RedisProperties redisProperties) {
        boolean keepAlive = zoneProperties.parsedMode() == ZoneRedisProperties.DeployMode.CLOUD;
        Duration connectTimeout = redisProperties.getConnectTimeout();
        ZoneLog.info("ZoneRedisLegacyKeepAliveConfiguration", "zoneLettuceLegacyKeepAliveCustomizer",
                "zone-redis legacy keep-alive customizer registered (Boot 3.1-3.3): keepAlive={}", keepAlive);
        return builder -> {
            // SSL 场景跳过：整体替换 ClientOptions 会丢失 Boot 构建的 SslOptions
            if (redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled()) {
                return;
            }
            SocketOptions.Builder socketOptions = SocketOptions.builder().keepAlive(keepAlive);
            if (connectTimeout != null) {
                socketOptions.connectTimeout(connectTimeout);
            }
            builder.clientOptions(ClientOptions.builder()
                    .socketOptions(socketOptions.build())
                    .timeoutOptions(TimeoutOptions.enabled())
                    .build());
        };
    }
}
