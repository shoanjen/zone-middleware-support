package com.zone.middleware.security.token;

import com.alibaba.fastjson2.JSON;
import com.zone.middleware.common.util.ZoneRandomUtils;
import com.zone.middleware.log.ZoneLog;
import com.zone.middleware.security.config.ZoneSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 登录令牌服务：不透明令牌（opaque token）+ Redis 有状态会话。
 *
 * <p>存储设计：</p>
 * <ul>
 *   <li>{@code zone:security:token:<token>} → 用户信息 JSON 串，TTL = token.ttl；</li>
 *   <li>{@code zone:security:user-token:<userId>} → Set(token 集合)，供踢下线与单端互斥枚举。</li>
 * </ul>
 *
 * <p>策略：默认滑动续期（校验通过时生存期重置为完整 TTL，可关）；默认多端并存，
 * 可配置单端互斥（新登录撤销该用户全部旧令牌）。令牌经请求头 {@code Zone-Auth-Token}
 * 携带（见 {@code ZoneAuthTokenResolver}）。</p>
 */
public class ZoneTokenService {

    /** 类名（ZoneLog 埋点用） */
    private static final String CLASS_NAME = "ZoneTokenService";

    /** 令牌 key 前缀 */
    private static final String TOKEN_KEY_PREFIX = "zone:security:token:";

    /** 用户令牌集合 key 前缀 */
    private static final String USER_TOKEN_KEY_PREFIX = "zone:security:user-token:";

    /** 令牌长度：43 位字母数字（约 256 位随机熵，≥128 位要求） */
    private static final int TOKEN_LENGTH = 43;

    /** 安全配置 */
    private final ZoneSecurityProperties properties;

    /** 令牌与用户令牌集合的 Redis 底座模板 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 构建登录令牌服务。
     *
     * @param properties     zone.middleware.security.* 配置
     * @param redisTemplate  字符串 Redis 模板
     */
    public ZoneTokenService(ZoneSecurityProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 签发登录令牌：加密安全随机数生成不透明令牌，Redis 记录令牌到用户信息的映射。
     * 单端互斥策略下先撤销该用户全部既有令牌。
     *
     * @param userId   用户唯一标识，不能为空
     * @param userInfo 登录用户信息（以 fastjson2 JSON 串存储，解析时按目标类型还原）
     * @return 不透明令牌字符串
     * @throws IllegalArgumentException userId 为空或 userInfo 为 null
     */
    public String issueToken(String userId, Object userInfo) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (userInfo == null) {
            throw new IllegalArgumentException("userInfo 不能为 null");
        }

        if (!properties.getToken().isMultiDevice()) {
            revokeUserTokens(userId);
        }

        String token = ZoneRandomUtils.randomString(TOKEN_LENGTH);
        Duration ttl = properties.getToken().getTtl();
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, JSON.toJSONString(userInfo), ttl);
        redisTemplate.opsForSet().add(userTokenKey(userId), token);

        ZoneLog.info(CLASS_NAME, "issueToken", "令牌已签发, userId: {}, ttl: {}", userId, ttl);
        return token;
    }

    /**
     * 解析登录令牌为用户信息原始 JSON 串：合法且未过期时返回签发时保存的 JSON 串；
     * 不存在、已过期、已撤销的令牌返回 null 而不抛异常。
     * 默认配置下校验通过即滑动续期（生存期重置为完整 TTL）。
     *
     * <p>调用方无需感知业务用户类型时（如签名校验拦截器建立登录上下文）使用本方法；
     * 需要类型化结果时直接使用 {@link #parseToken(String, Class)}。</p>
     *
     * @param token 令牌字符串，可为 null
     * @return 用户信息原始 JSON 串；令牌无效返回 null
     */
    public String parseTokenRaw(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String tokenKey = TOKEN_KEY_PREFIX + token;
        String userInfoJson = redisTemplate.opsForValue().get(tokenKey);
        if (userInfoJson == null) {
            return null;
        }

        if (properties.getToken().isRenewSliding()) {
            redisTemplate.expire(tokenKey, properties.getToken().getTtl());
        }

        return userInfoJson;
    }

    /**
     * 解析登录令牌：合法且未过期时返回签发时保存的用户信息；
     * 不存在、已过期、已撤销的令牌返回 null 而不抛异常。
     * 默认配置下校验通过即滑动续期（生存期重置为完整 TTL）。
     *
     * @param token 令牌字符串，可为 null
     * @param type  用户信息目标类型
     * @param <T>   用户信息类型
     * @return 用户信息；令牌无效返回 null
     */
    public <T> T parseToken(String token, Class<T> type) {
        String userInfoJson = parseTokenRaw(token);
        return userInfoJson == null ? null : JSON.parseObject(userInfoJson, type);
    }

    /**
     * 按令牌撤销（登出）：立即生效。
     *
     * @param token 待撤销的令牌，可为 null（null 直接忽略）
     */
    public void revokeToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        String userInfoJson = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        redisTemplate.delete(TOKEN_KEY_PREFIX + token);
        if (userInfoJson != null) {
            // user-token 集合不随 token key 过期，撤销时顺手清理成员（惰性回收）
            String userId = extractUserId(userInfoJson);
            if (userId != null) {
                redisTemplate.opsForSet().remove(userTokenKey(userId), token);
            }
        }
        ZoneLog.info(CLASS_NAME, "revokeToken", "令牌已撤销");
    }

    /**
     * 按用户撤销其全部令牌（踢下线）：立即生效。
     *
     * @param userId 用户唯一标识，可为 null（null 直接忽略）
     */
    public void revokeUserTokens(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        String userTokenKey = userTokenKey(userId);
        var tokens = redisTemplate.opsForSet().members(userTokenKey);
        if (tokens != null && !tokens.isEmpty()) {
            redisTemplate.delete(tokens.stream().map(token -> TOKEN_KEY_PREFIX + token).toList());
        }
        redisTemplate.delete(userTokenKey);
        ZoneLog.info(CLASS_NAME, "revokeUserTokens", "用户令牌已全部撤销, userId: {}", userId);
    }

    /**
     * 从存储的用户信息 JSON 串提取 userId 字段（撤销回收集合用）。
     *
     * @param userInfoJson 用户信息 JSON 串
     * @return userId；无法提取返回 null（集合成员由踢下线路径兜底清理）
     */
    private String extractUserId(String userInfoJson) {
        try {
            return JSON.parseObject(userInfoJson).getString("userId");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 用户令牌集合 key。
     *
     * @param userId 用户唯一标识
     * @return 完整 key
     */
    private String userTokenKey(String userId) {
        return USER_TOKEN_KEY_PREFIX + userId;
    }
}
