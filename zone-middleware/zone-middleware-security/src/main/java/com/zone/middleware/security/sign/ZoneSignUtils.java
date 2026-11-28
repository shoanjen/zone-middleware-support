package com.zone.middleware.security.sign;

import com.zone.middleware.common.util.JsonUtil;
import com.zone.middleware.common.util.ZoneRandomUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 接口签名工具：调用方与服务端共用的同一套签名实现。
 *
 * <p>签名规范（两端必须一致，见 api-sign-auth 能力规格）：</p>
 * <ol>
 *   <li>取请求顶层全部参与参数（BaseRequest 四字段 reqId/timestamp/platform/deviceId
 *       与业务参数），值为 null 或空串的参数剔除；</li>
 *   <li>按参数名升序排列（TreeMap），以 {@code k1=v1&k2=v2} 连接；</li>
 *   <li>末尾追加 {@code &key=<secret>}；</li>
 *   <li>对整串计算 MD5，得到 32 位小写十六进制签名。</li>
 * </ol>
 *
 * <p>参数值转换规则：字符串原样参与；其余类型（Number/Boolean/JSONObject/JSONArray/POJO）
 * 以 fastjson2 默认序列化结果参与——嵌套结构因此保持插入序，调用方构建 Map 时须使用
 * 保持插入序的实现（如 {@code LinkedHashMap}、{@code JSONObject}）或 POJO，禁止
 * {@code HashMap}（无序导致双端拼串不一致）。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * Map<String, Object> params = new LinkedHashMap<>();
 * params.put("reqId", ZoneSignUtils.generateReqId());
 * params.put("timestamp", System.currentTimeMillis());
 * params.put("platform", "web");
 * params.put("orderNo", "A1");
 * String sign = ZoneSignUtils.sign(params, secret);
 * // 放入请求头 Zone-Access-Token
 * }</pre>
 */
public final class ZoneSignUtils {

    /** 摘要算法（设计单点收口：升级算法只改此处） */
    private static final String DIGEST_ALGORITHM = "MD5";

    /** reqId 长度：32 位无连接符字母数字随机串 */
    private static final int REQ_ID_LENGTH = 32;

    /** secret 追加形态的字段名 */
    private static final String SECRET_FIELD = "key";

    /** 参数间连接符 */
    private static final char PARAM_JOINER = '&';

    /** 键值分隔符 */
    private static final char KV_JOINER = '=';

    /** 十六进制字符表（小写） */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /** 工具类禁止实例化 */
    private ZoneSignUtils() {
    }

    /**
     * 生成接口请求标识 reqId：32 位字母数字随机串，不含连接符。
     *
     * @return 32 位随机字符串（每次调用均不同）
     */
    public static String generateReqId() {
        return ZoneRandomUtils.randomString(REQ_ID_LENGTH);
    }

    /**
     * 按签名规范计算签名。
     *
     * @param params 参与签名的顶层参数（键为参数名；null 或空串值自动剔除）
     * @param secret 双端约定的签名密钥，不能为空
     * @return 32 位小写十六进制签名
     * @throws IllegalArgumentException secret 为空或参数键为 null
     */
    public static String sign(Map<String, ?> params, String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        String canonicalString = buildCanonicalString(params, secret);
        return digestLowerHex(canonicalString);
    }

    /**
     * 构建待签名串：空值剔除、TreeMap 升序、{@code k1=v1&} 连接、末尾追加 {@code &key=<secret>}。
     *
     * @param params 参与签名的顶层参数
     * @param secret 签名密钥（调用方已保证非空）
     * @return 待签名串
     * @throws IllegalArgumentException 参数键为 null
     */
    private static String buildCanonicalString(Map<String, ?> params, String secret) {
        TreeMap<String, String> sortedParams = new TreeMap<>();
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            String paramValue = convertValue(entry.getValue());
            if (paramValue == null) {
                continue;
            }
            String paramKey = entry.getKey();
            if (paramKey == null) {
                throw new IllegalArgumentException("参数名不能为 null");
            }
            sortedParams.put(paramKey, paramValue);
        }

        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (canonical.length() > 0) {
                canonical.append(PARAM_JOINER);
            }
            canonical.append(entry.getKey()).append(KV_JOINER).append(entry.getValue());
        }
        canonical.append(PARAM_JOINER).append(SECRET_FIELD).append(KV_JOINER).append(secret);
        return canonical.toString();
    }

    /**
     * 参数值转参与拼串的文本：字符串原样，其余类型以 fastjson2 序列化结果参与；转换后为空串剔除。
     *
     * @param value 原始参数值，可为 null
     * @return 参与拼串的文本；null 表示该参数不参与
     */
    private static String convertValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof String ? (String) value : JsonUtil.toJson(value);
        return text.isEmpty() ? null : text;
    }

    /**
     * 计算文本摘要并转为 32 位小写十六进制。
     *
     * @param text 待摘要文本
     * @return 小写十六进制摘要
     */
    private static String digestLowerHex(String text) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持摘要算法: " + DIGEST_ALGORITHM, e);
        }
        byte[] hashed = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hashed.length * 2);
        for (byte b : hashed) {
            hex.append(HEX_CHARS[(b >> 4) & 0x0f]).append(HEX_CHARS[b & 0x0f]);
        }
        return hex.toString();
    }
}
