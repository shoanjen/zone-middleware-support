package com.zone.middleware.redis.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 连接共享 Redis 实例（localhost:6379，db 15 隔离）的集成测试；
 * 实例不可达时整类跳过，不阻塞构建。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZoneRedisTemplateIntegrationTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String name;
        private int age;
    }

    private static final String PREFIX = "zone:test:";
    private static final int DB_INDEX = 15;

    private LettuceConnectionFactory factory;
    private StringRedisTemplate stringTemplate;
    private ZoneRedisTemplate redis;

    @BeforeAll
    void setUp() {
        assumeTrue(isRedisReachable(), "共享 Redis (localhost:6379) 不可达，跳过集成测试");

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        configuration.setDatabase(DB_INDEX);
        factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        stringTemplate = new StringRedisTemplate(factory);
        redis = new ZoneRedisTemplate(stringTemplate);
    }

    @AfterAll
    void tearDown() {
        if (stringTemplate != null) {
            stringTemplate.delete(List.of(
                    PREFIX + "str", PREFIX + "str:ttl", PREFIX + "hash",
                    PREFIX + "list", PREFIX + "set", PREFIX + "zset",
                    PREFIX + "b1", PREFIX + "b2", PREFIX + "b3"));
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    private static boolean isRedisReachable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 6379), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void stringSetGetDeleteWithTtl() {
        String key = PREFIX + "str";
        redis.set(key, new User("zone", 3), Duration.ofSeconds(60));
        User loaded = redis.get(key, User.class);
        assertEquals("zone", loaded.getName());
        assertEquals(3, loaded.getAge());

        assertTrue(redis.delete(key));
        assertNull(redis.get(key, User.class));
    }

    @Test
    void hashFieldLevelOperations() {
        String key = PREFIX + "hash";
        redis.hSet(key, "f1", new User("a", 1));
        redis.hSet(key, "f2", new User("b", 2));

        assertEquals("a", redis.hGet(key, "f1", User.class).getName());

        Map<String, User> all = redis.hGetAll(key, User.class);
        assertEquals(2, all.size());
        assertEquals(2, all.get("f2").getAge());

        redis.hDel(key, "f1");
        assertNull(redis.hGet(key, "f1", User.class));
        assertEquals("b", redis.hGet(key, "f2", User.class).getName());
    }

    @Test
    void listPushRangeSetRemove() {
        String key = PREFIX + "list";
        redis.rPush(key, "one");
        redis.rPush(key, "two");
        redis.rPush(key, "three");

        List<String> range = redis.lRange(key, 0, -1, String.class);
        assertEquals(List.of("one", "two", "three"), range);
        assertEquals(3L, redis.lSize(key));

        redis.lSet(key, 1, "TWO");
        assertEquals(List.of("one", "TWO", "three"), redis.lRange(key, 0, -1, String.class));

        redis.lRem(key, 1, "TWO");
        assertEquals(List.of("one", "three"), redis.lRange(key, 0, -1, String.class));
    }

    @Test
    void setMemberOperations() {
        String key = PREFIX + "set";
        redis.sAdd(key, "m1", "m2");

        assertTrue(redis.sIsMember(key, "m1"));
        assertEquals(2L, redis.sCard(key));

        Set<String> members = redis.sMembers(key, String.class);
        assertTrue(members.contains("m1") && members.contains("m2"));

        redis.sRem(key, "m1");
        assertFalse(redis.sIsMember(key, "m1"));
        assertTrue(redis.sIsMember(key, "m2"));
    }

    @Test
    void zSetScoredOperations() {
        String key = PREFIX + "zset";
        redis.zAdd(key, "low", 1.0);
        redis.zAdd(key, "mid", 5.0);
        redis.zAdd(key, "high", 9.0);

        assertEquals(3L, redis.zCard(key));
        assertEquals(5.0, redis.zScore(key, "mid"));

        List<String> asc = redis.zRange(key, 0, -1, String.class);
        assertEquals(List.of("low", "mid", "high"), asc);
        List<String> desc = redis.zRevRange(key, 0, -1, String.class);
        assertEquals(List.of("high", "mid", "low"), desc);

        redis.zRem(key, "mid");
        assertEquals(List.of("low", "high"), redis.zRange(key, 0, -1, String.class));
    }

    @Test
    void batchGetAndBatchDelete() {
        redis.set(PREFIX + "b1", new User("x", 10));
        redis.set(PREFIX + "b2", new User("y", 20));
        redis.set(PREFIX + "b3", "plain");

        List<User> users = redis.mGet(
                List.of(PREFIX + "b1", PREFIX + "b2", PREFIX + "missing"), User.class);
        assertEquals(3, users.size());
        assertEquals("x", users.get(0).getName());
        assertEquals("y", users.get(1).getName());
        assertNull(users.get(2));
        assertEquals("plain", redis.get(PREFIX + "b3", String.class));

        Long deleted = redis.deleteKeys(List.of(PREFIX + "b1", PREFIX + "b2", PREFIX + "b3"));
        assertEquals(3L, deleted);
        assertNull(redis.get(PREFIX + "b1", User.class));
    }

    @Test
    void storedValueShouldBeHumanReadableJson() {
        String key = PREFIX + "str:ttl";
        redis.set(key, new User("json-check", 7), Duration.ofSeconds(60));
        String raw = stringTemplate.opsForValue().get(key);
        assertTrue(raw.contains("\"name\":\"json-check\"") && raw.contains("\"age\":7"),
                "存储值应为 JSON 字符串: " + raw);
    }
}
