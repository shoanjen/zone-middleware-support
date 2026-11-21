package com.zone.middleware.security.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;

/**
 * 登录用户上下文：强制登录端点（{@code @ZoneSignCheck(requireLogin = true)}）校验通过后
 * 由拦截器构建并写入 request attribute，业务代码经 {@code ZoneSecurityContext} 门面取用。
 *
 * <p>中间件不感知业务用户类型：登录令牌在 Redis 侧存储的即为任意类型用户信息的 JSON 串，
 * 上下文仅保存原始串与通用字段（token、userId），业务类型经 {@link #as(Class)} 惰性解析。</p>
 *
 * <p>userId 取用户信息 JSON 的 {@code userId} 字段（与 {@code ZoneTokenService} 撤销路径
 * 的既有约定一致）；字段缺失时 userId 透传 null，不阻断请求。</p>
 */
@Getter
public class ZoneUserContext {

    /** request attribute 名：登录用户上下文 */
    public static final String REQUEST_ATTRIBUTE = "zone.security.userContext";

    /** 登录令牌串（请求头 {@code Zone-Auth-Token} 携带值） */
    private final String token;

    /** 用户标识：用户信息 JSON 的 userId 字段；缺失为 null */
    private final String userId;

    /** 用户信息原始 JSON 串（Redis 存储侧原样回传） */
    private final String rawJson;

    /**
     * 构建登录用户上下文。
     *
     * @param token   登录令牌串
     * @param rawJson 用户信息原始 JSON 串
     */
    public ZoneUserContext(String token, String rawJson) {
        this.token = token;
        this.rawJson = rawJson;
        String extractedUserId;
        try {
            JSONObject parsed = JSON.parseObject(rawJson);
            extractedUserId = parsed == null ? null : parsed.getString("userId");
        } catch (RuntimeException e) {
            // 存储内容异常不阻断请求：userId 透传 null，业务类型解析时再显式报错
            extractedUserId = null;
        }
        this.userId = extractedUserId;
    }

    /**
     * 按业务类型解析用户信息（fastjson2 反序列化原始 JSON 串）。
     *
     * @param type 业务用户类型
     * @param <T>  用户信息类型
     * @return 用户信息实例
     * @throws com.alibaba.fastjson2.JSONException 业务类型与存储结构不符（编码错误，显式暴露）
     */
    public <T> T as(Class<T> type) {
        return JSON.parseObject(rawJson, type);
    }
}
