package com.zone.middleware.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 操作系统枚举。
 */
@Getter
@RequiredArgsConstructor
public enum OsType {

    /** 安卓 */
    ANDROID("android"),
    /** 苹果 iOS / iPadOS */
    IOS("ios"),
    /** 微软 Windows */
    WINDOWS("windows"),
    /** 华为鸿蒙 Harmony */
    HARMONY("harmony"),
    /** Linux 桌面 / 服务器 */
    LINUX("linux");

    /** 操作系统标识（协议使用小写值） */
    private final String value;

    /**
     * 容错解析操作系统：按标识大小写不敏感匹配（枚举名即 value 的大写形式，ANDROID/android 均可命中）。
     *
     * @param value 操作系统标识字符串，可为 null
     * @return 对应操作系统枚举；null、空串或未知值返回 null（外部输入不抛异常）
     */
    public static OsType of(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (OsType candidate : values()) {
            if (candidate.name().equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        return null;
    }
}
