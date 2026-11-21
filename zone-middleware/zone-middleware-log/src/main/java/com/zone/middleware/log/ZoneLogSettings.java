package com.zone.middleware.log;

import lombok.Getter;

/**
 * ZoneLog 运行时设置（不可变快照，整体原子替换）。
 * <p>默认：应用名 unknown、级别门槛 INFO、包含应用名与 traceId。</p>
 */
@Getter
public final class ZoneLogSettings {

    private static volatile ZoneLogSettings current =
            new ZoneLogSettings("unknown", ZoneLogLevel.INFO, true, true);

    /** 当前生效设置快照 */
    private final String appName;
    private final ZoneLogLevel level;
    private final boolean includeTraceId;
    private final boolean includeAppName;

    /**
     * 私有构造（不可变快照）。
     *
     * @param appName        应用名
     * @param level          级别门槛
     * @param includeTraceId 是否输出 traceId 段
     * @param includeAppName 是否输出应用名段
     */
    private ZoneLogSettings(String appName, ZoneLogLevel level, boolean includeTraceId, boolean includeAppName) {
        this.appName = appName;
        this.level = level;
        this.includeTraceId = includeTraceId;
        this.includeAppName = includeAppName;
    }

    /**
     * 应用整体替换当前设置（null 值按默认回退）。
     *
     * @param appName        应用名；null/空回退 unknown
     * @param level          级别门槛；null 回退 INFO
     * @param includeTraceId 是否输出 traceId 段
     * @param includeAppName 是否输出应用名段
     */
    public static void apply(String appName, ZoneLogLevel level, boolean includeTraceId, boolean includeAppName) {
        current = new ZoneLogSettings(
                appName == null || appName.isEmpty() ? "unknown" : appName,
                level == null ? ZoneLogLevel.INFO : level,
                includeTraceId,
                includeAppName);
    }

    /**
     * 获取当前生效设置。
     *
     * @return 全局唯一设置快照
     */
    public static ZoneLogSettings current() {
        return current;
    }

    /** 恢复默认设置（仅测试/重置用）。 */
    static void reset() {
        apply("unknown", ZoneLogLevel.INFO, true, true);
    }
}
