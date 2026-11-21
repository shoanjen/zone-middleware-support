package com.zone.middleware.common.util;

import java.security.SecureRandom;

/**
 * 随机字符串生成工具：密码学安全随机源（SecureRandom），默认字符集 [a-zA-Z0-9]。
 * <p>支持分段模式：每段固定分段长度的随机串，段间以连接符连接，输出总长度恒等于指定长度
 * （末段可能被截断到剩余长度）。</p>
 */
public final class ZoneRandomUtils {

    /** 默认字符集：小写字母 + 大写字母 + 数字 */
    private static final String CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** 密码学安全随机源（线程安全） */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 工具类禁止实例化 */
    private ZoneRandomUtils() {
    }

    /**
     * 生成指定长度的随机字符串（不分段）。
     *
     * @param length 输出长度，必须为正
     * @return 长度恰为 {@code length} 的字母数字随机串
     * @throws IllegalArgumentException length 非正数
     */
    public static String randomString(int length) {
        return randomString(length, (String) null, 0);
    }

    /**
     * 生成分段随机字符串（字符连接符便捷重载）。
     *
     * @param length        输出总长度，必须为正
     * @param connector     段间连接符
     * @param segmentLength 每段长度，必须为正
     * @return 总长度恰为 {@code length}、段间以连接符连接的随机串
     * @throws IllegalArgumentException length 或 segmentLength 非正数
     */
    public static String randomString(int length, char connector, int segmentLength) {
        return randomString(length, String.valueOf(connector), segmentLength);
    }

    /**
     * 生成分段随机字符串。
     * <p>连接符为 null 或空串时生成分段不生效的等长随机串；剩余空间不足以容纳
     * 「连接符 + 至少 1 个字符」时以随机字符补满，保证总长度恒定。</p>
     *
     * @param length        输出总长度，必须为正
     * @param connector     段间连接符；null 或空串表示不分段
     * @param segmentLength 每段长度，必须为正（连接符为空时忽略）
     * @return 总长度恰为 {@code length} 的字母数字随机串
     * @throws IllegalArgumentException length 或 segmentLength 非正数
     */
    public static String randomString(int length, String connector, int segmentLength) {
        if (length <= 0) {
            throw new IllegalArgumentException("length 必须为正数，当前值: " + length);
        }
        boolean segmented = connector != null && !connector.isEmpty();
        if (segmented && segmentLength <= 0) {
            throw new IllegalArgumentException("segmentLength 必须为正数，当前值: " + segmentLength);
        }
        if (!segmented) {
            return randomAlphaNumeric(length);
        }

        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            int available = length - sb.length();
            if (sb.length() > 0) {
                if (available > connector.length()) {
                    sb.append(connector);
                    available -= connector.length();
                } else {
                    // 剩余空间不足以容纳「连接符+至少1个字符」：直接以随机字符补满
                    sb.append(randomAlphaNumeric(available));
                    break;
                }
            }
            sb.append(randomAlphaNumeric(Math.min(segmentLength, available)));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度的纯字母数字随机串。
     *
     * @param length 长度（调用方保证为正）
     * @return 恰为 {@code length} 个字符的随机串
     */
    private static String randomAlphaNumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
