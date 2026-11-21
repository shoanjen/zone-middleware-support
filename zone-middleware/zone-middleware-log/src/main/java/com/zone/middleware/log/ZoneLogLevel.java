package com.zone.middleware.log;

import lombok.RequiredArgsConstructor;

/**
 * ZoneLog 输出级别。
 */
@RequiredArgsConstructor
public enum ZoneLogLevel {

    /** 调试：开发期诊断信息，默认门槛（INFO）下不输出 */
    DEBUG(0),

    /** 信息：常规业务埋点，默认输出级别 */
    INFO(1),

    /** 警告：可自动恢复的异常状况 */
    WARN(2),

    /** 错误：需要人工介入的失败 */
    ERROR(3);

    private final int rank;

    /**
     * 获取级别序数，用于门槛比较：值越大级别越高。
     *
     * @return 级别序数（DEBUG=0 … ERROR=3）
     */
    public int rank() {
        return rank;
    }
}
