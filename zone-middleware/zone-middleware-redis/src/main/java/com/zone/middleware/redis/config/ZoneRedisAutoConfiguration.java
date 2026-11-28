package com.zone.middleware.redis.config;

import com.zone.middleware.log.ZoneLog;
import com.zone.middleware.redis.service.ZoneRedisTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis 支撑自动装配：连接工厂由 Spring Boot 官方 RedisAutoConfiguration 按
 * spring.data.redis.* 装配（本类不建工厂），本类只做模板装配与启动日志输出；
 * 连接与池参数（超时、连接池、TCP 保活定制）完全由官方键承载，未配置项回归官方默认。
 */
@AutoConfiguration
@ConditionalOnClass(LettuceConnectionFactory.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class ZoneRedisAutoConfiguration {

    /**
     * 构建字符串 Redis 模板（key/value 均字符串序列化），连接工厂来自官方装配。
     *
     * @param connectionFactory 官方装配的 Lettuce 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate zoneStringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 构建 zone 统一操作模板，启动时输出官方键的生效连接参数。
     *
     * @param zoneStringRedisTemplate 字符串 Redis 模板
     * @param redisProperties         官方连接配置（用于启动日志）
     * @return ZoneRedisTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(ZoneRedisTemplate.class)
    public ZoneRedisTemplate zoneRedisTemplate(StringRedisTemplate zoneStringRedisTemplate,
                                               RedisProperties redisProperties) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("host", redisProperties.getHost() + ":" + redisProperties.getPort());
        info.put("database", redisProperties.getDatabase());
        info.put("connectTimeout", redisProperties.getConnectTimeout());
        info.put("commandTimeout", redisProperties.getTimeout());
        info.put("poolMaxActive", redisProperties.getLettuce().getPool().getMaxActive());
        ZoneLog.info("ZoneRedisAutoConfiguration", "zoneRedisTemplate",
                "zone-redis enabled: {}", info);
        return new ZoneRedisTemplate(zoneStringRedisTemplate);
    }
}
