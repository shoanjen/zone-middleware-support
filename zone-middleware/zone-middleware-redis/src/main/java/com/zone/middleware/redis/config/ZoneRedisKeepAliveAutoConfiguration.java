package com.zone.middleware.redis.config;

import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * TCP 保活按部署模式应用（LOCAL 关 / CLOUD 开）——Boot 3.4+ 路径。
 *
 * <p>{@code LettuceClientOptionsBuilderCustomizer} 仅 Boot 3.4+ 存在，故以类级条件隔离在
 * 本配置类（老版本 Boot 条件不通过即整类跳过，不会内省方法签名）；定制器只替换
 * socketOptions（keepAlive + 显式回填官方 connectTimeout），不触碰 Boot 设置的
 * timeoutOptions 等其余 ClientOptions。</p>
 */
@AutoConfiguration
@ConditionalOnClass(LettuceClientOptionsBuilderCustomizer.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties({ZoneRedisProperties.class, RedisProperties.class})
public class ZoneRedisKeepAliveAutoConfiguration {

    /**
     * 按部署模式应用 Lettuce TCP 保活（Boot 3.4+）。
     *
     * @param zoneProperties  zone 部署模式
     * @param redisProperties 官方连接配置（取 connectTimeout 回填）
     * @return ClientOptions 定制器
     */
    @Bean
    public LettuceClientOptionsBuilderCustomizer zoneLettuceKeepAliveCustomizer(
            ZoneRedisProperties zoneProperties, RedisProperties redisProperties) {
        boolean keepAlive = zoneProperties.parsedMode() == ZoneRedisProperties.DeployMode.CLOUD;
        return builder -> {
            SocketOptions.Builder socketOptions = SocketOptions.builder().keepAlive(keepAlive);
            Duration connectTimeout = redisProperties.getConnectTimeout();
            if (connectTimeout != null) {
                socketOptions.connectTimeout(connectTimeout);
            }
            builder.socketOptions(socketOptions.build());
        };
    }
}
