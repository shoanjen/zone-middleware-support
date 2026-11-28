package com.zone.middleware.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

/**
 * Zone 生态统一 JSON 工具（fastjson2 底座）。
 *
 * <p>生态内模块（redis / http / security / common）的 JSON 序列化与反序列化统一经本工具收口，
 * 便于未来全局行为治理（统一序列化特性、日期格式、敏感字段过滤等）。</p>
 *
 * <pre>{@code
 * String json = JsonUtil.toJson(order);
 * Order parsed = JsonUtil.fromJson(json, Order.class);
 * ZoneResult<List<Order>> page = JsonUtil.fromJson(json, new TypeReference<>() {});
 * }</pre>
 */
public final class  JsonUtil {

    /** 工具类禁止实例化 */
    private JsonUtil() {
    }

    /**
     * 序列化为 JSON 文本。
     *
     * @param value 待序列化对象，可为 null
     * @return JSON 字符串；value 为 null 时返回 null
     */
    public static String toJson(Object value) {
        return value == null ? null : JSON.toJSONString(value);
    }

    /**
     * 反序列化为目标类型。
     *
     * @param json JSON 文本，可为 null 或空
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化对象；json 为 null 或空时返回 null
     */
    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, type);
    }

    /**
     * 反序列化为泛型目标类型（支持完整泛型树，data 不退化为 JSONObject）。
     *
     * @param json JSON 文本，可为 null 或空
     * @param type 泛型类型声明（如 {@code new TypeReference<ZoneResult<Order>>() {}}）
     * @param <T>  目标类型
     * @return 反序列化对象；json 为 null 或空时返回 null
     */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, type);
    }
}
