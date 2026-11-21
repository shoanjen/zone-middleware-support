package com.zone.middleware.redis.config;

import com.zone.middleware.log.ZoneLog;
import com.zone.middleware.redis.ZoneRedisTemplate;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis 支撑自动装配：按 zone.middleware.redis.* 构建 Lettuce 连接（含连接池），
 * 启动时输出当前部署模式与生效超时。
 *
 * <p>必须排在 Spring 的 RedisAutoConfiguration 之前（按类名字母序 com.zone 靠后，
 * 故显式声明 Before），否则 Spring 会先用 spring.data.redis.* 默认值建连接导致 zone 配置失效。</p>
 */
@Configuration
@ConditionalOnClass(LettuceConnectionFactory.class)
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties(ZoneRedisProperties.class)
public class ZoneRedisAutoConfiguration {

    /**
     * 构建 Lettuce 连接工厂（含连接池），启动时输出部署模式与生效超时。
     *
     * @param properties 绑定的 zone.middleware.redis.* 配置
     * @return 已完成初始化的 Lettuce 连接工厂
     * @throws IllegalArgumentException mode 配置为非法值时
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean(LettuceConnectionFactory.class)
    public LettuceConnectionFactory zoneRedisConnectionFactory(ZoneRedisProperties properties) {
        ZoneRedisProperties.DeployMode mode = properties.parsedMode();

        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                properties.getHost(), properties.getPort());
        server.setDatabase(properties.getDatabase());
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            server.setPassword(properties.getPassword());
        }

        ZoneRedisProperties.Pool pool = properties.getPool();
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(pool.getMaxActive());
        poolConfig.setMaxIdle(pool.getMaxIdle());
        poolConfig.setMinIdle(pool.getMinIdle());
        poolConfig.setMaxWait(pool.getMaxWait());
        poolConfig.setBlockWhenExhausted(true);

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(properties.resolvedConnectTimeout())
                .keepAlive(properties.resolvedKeepAlive())
                .build();
        // spring-data-redis 2.7 的 builder 不直接暴露 socketOptions，经 ClientOptions 传入
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .build();

        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
                LettucePoolingClientConfiguration.builder()
                        .commandTimeout(properties.resolvedCommandTimeout())
                        .clientOptions(clientOptions)
                        .poolConfig(poolConfig);
        if (properties.isSsl()) {
            builder.useSsl();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, builder.build());
        factory.afterPropertiesSet();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", mode);
        info.put("host", properties.getHost() + ":" + properties.getPort());
        info.put("database", properties.getDatabase());
        info.put("connectTimeout", properties.resolvedConnectTimeout());
        info.put("commandTimeout", properties.resolvedCommandTimeout());
        info.put("keepAlive", properties.resolvedKeepAlive());
        info.put("ssl", properties.isSsl());
        ZoneLog.info("ZoneRedisAutoConfiguration", "zoneRedisConnectionFactory",
                "zone-redis enabled: {}", info);
        return factory;
    }

    /**
     * 构建字符串 Redis 模板（key/value 均字符串序列化）。
     *
     * @param zoneRedisConnectionFactory zone 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate zoneStringRedisTemplate(LettuceConnectionFactory zoneRedisConnectionFactory) {
        return new StringRedisTemplate(zoneRedisConnectionFactory);
    }

    /**
     * 构建 zone 统一操作模板。
     *
     * @param zoneStringRedisTemplate 字符串 Redis 模板
     * @return ZoneRedisTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(ZoneRedisTemplate.class)
    public ZoneRedisTemplate zoneRedisTemplate(StringRedisTemplate zoneStringRedisTemplate) {
        return new ZoneRedisTemplate(zoneStringRedisTemplate);
    }
}
