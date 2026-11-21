package com.zone.middleware.security.token;

import com.alibaba.fastjson2.JSON;
import com.zone.middleware.security.config.ZoneSecurityProperties;
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

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 登录令牌服务集成测试：连接共享 Redis 实例（localhost:6379，db 14 隔离）；
 * 实例不可达时整类跳过，不阻塞构建。逐一覆盖 login-token 规格场景。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZoneTokenServiceIntegrationTest {

    /** 登录用户信息样例 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionUser {
        private String userId;
        private String userName;
    }

    private static final String KEY_PREFIX = "zone:security:test:";
    private static final int DB_INDEX = 14;

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redisTemplate;
    private ZoneTokenService tokenService;

    @BeforeAll
    void setUp() {
        assumeTrue(isRedisReachable(), "共享 Redis (localhost:6379) 不可达，跳过集成测试");

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        configuration.setDatabase(DB_INDEX);
        factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
        tokenService = new ZoneTokenService(new ZoneSecurityProperties(), redisTemplate);
    }

    @AfterAll
    void tearDown() {
        if (redisTemplate == null) {
            return;
        }
        // 服务自身 key 前缀为 zone:security:token:/user-token:，db 14 为测试专用库，整体清理
        Set<String> testKeys = redisTemplate.keys("zone:security:*");
        if (testKeys != null && !testKeys.isEmpty()) {
            redisTemplate.delete(testKeys);
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void issuedTokenShouldParseBackUserInfo() {
        String tokenA = tokenService.issueToken("u-1001", new SessionUser("u-1001", "张三"));
        String tokenB = tokenService.issueToken("u-1001", new SessionUser("u-1001", "张三"));

        // 43 位字母数字不透明令牌，两次签发不同
        assertTrue(tokenA.matches("[a-zA-Z0-9]{43}"), "令牌应为 43 位字母数字: " + tokenA);
        assertNotEquals(tokenA, tokenB);

        SessionUser parsed = tokenService.parseToken(tokenA, SessionUser.class);
        assertNotNull(parsed);
        assertEquals("u-1001", parsed.getUserId());
        assertEquals("张三", parsed.getUserName());
    }

    @Test
    void defaultTtlShouldBeSevenDays() {
        String token = tokenService.issueToken("u-ttl-default", new SessionUser("u-ttl-default", "李四"));

        Long expireSeconds = redisTemplate.getExpire("zone:security:token:" + token);
        assertNotNull(expireSeconds);
        // 允许执行耗时余量 60 秒
        assertTrue(expireSeconds > Duration.ofDays(7).toSeconds() - 60,
                "默认生存期应为 7 天，实际: " + expireSeconds + "s");
    }

    @Test
    void customTtlShouldBeHonored() {
        ZoneSecurityProperties properties = new ZoneSecurityProperties();
        properties.getToken().setTtl(Duration.ofHours(1));
        ZoneTokenService customService = new ZoneTokenService(properties, redisTemplate);

        String token = customService.issueToken("u-ttl-custom", new SessionUser("u-ttl-custom", "王五"));

        Long expireSeconds = redisTemplate.getExpire("zone:security:token:" + token);
        assertNotNull(expireSeconds);
        assertTrue(expireSeconds > Duration.ofHours(1).toSeconds() - 60,
                "自定义生存期应为 1 小时，实际: " + expireSeconds + "s");
    }

    @Test
    void invalidTokenShouldReturnNullQuietly() {
        assertNull(tokenService.parseToken(null, SessionUser.class));
        assertNull(tokenService.parseToken("", SessionUser.class));
        assertNull(tokenService.parseToken("not-exists-token", SessionUser.class));
    }

    // ==================== parseTokenRaw（原始 JSON 串解析） ====================

    @Test
    void parseTokenRawShouldReturnRawUserInfoJson() {
        String token = tokenService.issueToken("u-raw", new SessionUser("u-raw", "张三"));

        String rawJson = tokenService.parseTokenRaw(token);
        assertNotNull(rawJson);
        assertTrue(rawJson.contains("u-raw"));
        assertTrue(rawJson.contains("张三"));

        // 原始串可按业务类型还原（与 parseToken 结果一致）
        SessionUser parsed = JSON.parseObject(rawJson, SessionUser.class);
        assertEquals("u-raw", parsed.getUserId());
        assertEquals("张三", parsed.getUserName());
    }

    @Test
    void parseTokenRawShouldReturnNullForInvalidToken() {
        assertNull(tokenService.parseTokenRaw(null));
        assertNull(tokenService.parseTokenRaw(""));
        assertNull(tokenService.parseTokenRaw("not-exists-token"));
    }

    @Test
    void parseTokenRawShouldFailImmediatelyAfterRevoke() {
        String token = tokenService.issueToken("u-raw-revoke", new SessionUser("u-raw-revoke", "赵六"));
        assertNotNull(tokenService.parseTokenRaw(token));

        tokenService.revokeToken(token);
        assertNull(tokenService.parseTokenRaw(token));
    }

    @Test
    void parseTokenRawShouldSlideRenewalOnAccess() {
        ZoneSecurityProperties properties = new ZoneSecurityProperties();
        properties.getToken().setTtl(Duration.ofHours(1));
        ZoneTokenService slidingService = new ZoneTokenService(properties, redisTemplate);

        String token = slidingService.issueToken("u-raw-slide", new SessionUser("u-raw-slide", "孙八"));
        String tokenKey = "zone:security:token:" + token;
        // 人为压低生存期，模拟已存在 59 分钟的令牌
        redisTemplate.expire(tokenKey, Duration.ofSeconds(60));

        assertNotNull(slidingService.parseTokenRaw(token));

        Long expireSeconds = redisTemplate.getExpire(tokenKey);
        assertNotNull(expireSeconds);
        assertTrue(expireSeconds > Duration.ofHours(1).toSeconds() - 60,
                "parseTokenRaw 滑动续期后生存期应重置，实际: " + expireSeconds + "s");
    }

    @Test
    void revokedTokenShouldFailParseImmediately() {
        String token = tokenService.issueToken("u-logout", new SessionUser("u-logout", "赵六"));
        assertNotNull(tokenService.parseToken(token, SessionUser.class));

        tokenService.revokeToken(token);
        assertNull(tokenService.parseToken(token, SessionUser.class));
    }

    @Test
    void revokeUserTokensShouldInvalidateAll() {
        String tokenA = tokenService.issueToken("u-kick", new SessionUser("u-kick", "钱七"));
        String tokenB = tokenService.issueToken("u-kick", new SessionUser("u-kick", "钱七"));

        tokenService.revokeUserTokens("u-kick");

        assertNull(tokenService.parseToken(tokenA, SessionUser.class));
        assertNull(tokenService.parseToken(tokenB, SessionUser.class));
    }

    @Test
    void slidingRenewalShouldResetTtlOnAccess() {
        ZoneSecurityProperties properties = new ZoneSecurityProperties();
        properties.getToken().setTtl(Duration.ofHours(1));
        ZoneTokenService slidingService = new ZoneTokenService(properties, redisTemplate);

        String token = slidingService.issueToken("u-slide-on", new SessionUser("u-slide-on", "孙八"));
        String tokenKey = "zone:security:token:" + token;
        // 人为压低生存期，模拟已存在 59 分钟的令牌
        redisTemplate.expire(tokenKey, Duration.ofSeconds(60));

        assertNotNull(slidingService.parseToken(token, SessionUser.class));

        Long expireSeconds = redisTemplate.getExpire(tokenKey);
        assertNotNull(expireSeconds);
        assertTrue(expireSeconds > Duration.ofHours(1).toSeconds() - 60,
                "滑动续期后生存期应重置，实际: " + expireSeconds + "s");
    }

    @Test
    void renewalDisabledShouldKeepRemainingTtl() {
        ZoneSecurityProperties properties = new ZoneSecurityProperties();
        properties.getToken().setTtl(Duration.ofHours(1));
        properties.getToken().setRenewSliding(false);
        ZoneTokenService fixedService = new ZoneTokenService(properties, redisTemplate);

        String token = fixedService.issueToken("u-slide-off", new SessionUser("u-slide-off", "周九"));
        String tokenKey = "zone:security:token:" + token;
        redisTemplate.expire(tokenKey, Duration.ofSeconds(60));

        assertNotNull(fixedService.parseToken(token, SessionUser.class));

        Long expireSeconds = redisTemplate.getExpire(tokenKey);
        assertNotNull(expireSeconds);
        assertTrue(expireSeconds <= 60, "关闭续期后生存期不应重置，实际: " + expireSeconds + "s");
    }

    @Test
    void singleDeviceModeShouldKickOldTokens() {
        ZoneSecurityProperties properties = new ZoneSecurityProperties();
        properties.getToken().setMultiDevice(false);
        ZoneTokenService singleDeviceService = new ZoneTokenService(properties, redisTemplate);

        String oldToken = singleDeviceService.issueToken("u-single", new SessionUser("u-single", "吴十"));
        String newToken = singleDeviceService.issueToken("u-single", new SessionUser("u-single", "吴十"));

        assertNotNull(singleDeviceService.parseToken(newToken, SessionUser.class));
        assertNull(singleDeviceService.parseToken(oldToken, SessionUser.class));
    }

    /**
     * 共享 Redis 实例可达性探测。
     */
    private boolean isRedisReachable() {
        try (Socket socket = new Socket("127.0.0.1", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }
}
