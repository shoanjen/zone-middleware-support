package com.zone.middleware.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 计数/过期/简单互斥原语的集成测试：连接共享 Redis 实例（localhost:6379，db 15 隔离）；
 * 实例不可达时整类跳过，不阻塞构建。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZoneRedisPrimitivesIntegrationTest {

    private static final String PREFIX = "zone:test:prim:";

    private LettuceConnectionFactory factory;
    private StringRedisTemplate stringTemplate;
    private ZoneRedisTemplate redis;

    @BeforeAll
    void setUp() {
        assumeTrue(isRedisReachable(), "共享 Redis (localhost:6379) 不可达，跳过集成测试");

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        configuration.setDatabase(15);
        factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        stringTemplate = new StringRedisTemplate(factory);
        redis = new ZoneRedisTemplate(stringTemplate);
        // 清理残留，保证计数起点确定
        stringTemplate.delete(List.of(PREFIX + "count", PREFIX + "ttl", PREFIX + "lock"));
    }

    @AfterAll
    void tearDown() {
        stringTemplate.delete(List.of(PREFIX + "count", PREFIX + "ttl", PREFIX + "lock",
                PREFIX + "absent", PREFIX + "exists"));
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void incrShouldCountFromZero() {
        assertEquals(1L, redis.incr(PREFIX + "count"));
        assertEquals(2L, redis.incr(PREFIX + "count"));
        assertEquals(3L, redis.incr(PREFIX + "count"));
        assertEquals(4L, redis.incrBy(PREFIX + "count", 1));
        assertEquals(6L, redis.incrBy(PREFIX + "count", 2));
    }

    @Test
    void ttlShouldReportRemainingAndMissing() {
        redis.set(PREFIX + "ttl", "x", Duration.ofSeconds(60));
        Long remaining = redis.ttl(PREFIX + "ttl");
        assertEquals(true, remaining != null && remaining > 0 && remaining <= 60,
                "应返回剩余秒数: " + remaining);

        redis.expire(PREFIX + "ttl", 120);
        Long renewed = redis.ttl(PREFIX + "ttl");
        assertEquals(true, renewed > 60, "续期后剩余时间应超过原 60s: " + renewed);

        assertEquals(-2L, redis.ttl(PREFIX + "nonexist"), "不存在的键应返回 -2");
    }

    @Test
    void setIfAbsentShouldAcquireOnlyOnce() {
        assertEquals(true, redis.setIfAbsent(PREFIX + "lock", "owner-1", Duration.ofSeconds(30)),
                "首次抢占应成功");
        assertEquals(false, redis.setIfAbsent(PREFIX + "lock", "owner-2", Duration.ofSeconds(30)),
                "已存在时抢占应失败");
        assertEquals("\"owner-1\"", stringTemplate.opsForValue().get(PREFIX + "lock"),
                "抢占失败不得覆盖原值（fastjson2 JSON 存储）");
    }

    private static boolean isRedisReachable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 6379), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
