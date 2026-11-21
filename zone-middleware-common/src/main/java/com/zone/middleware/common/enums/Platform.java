package com.zone.middleware.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 请求平台枚举。
 */
@Getter
@RequiredArgsConstructor
public enum Platform {

    /** 浏览器 Web 端 */
    WEB("web"),
    /** PC 客户端 */
    PC("pc"),
    /** 移动端 */
    MOBILE("mobile"),
    /** 平板端 */
    PAD("pad");

    /** 平台标识（协议使用小写值） */
    private final String value;

    /**
     * 容错解析平台：按标识大小写不敏感匹配（枚举名即 value 的大写形式，WEB/web/Web 均可命中）。
     *
     * @param value 平台标识字符串，可为 null
     * @return 对应平台枚举；null、空串或未知值返回 null（外部输入不抛异常）
     */
    public static Platform of(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (Platform candidate : values()) {
            if (candidate.name().equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        return null;
    }
}
