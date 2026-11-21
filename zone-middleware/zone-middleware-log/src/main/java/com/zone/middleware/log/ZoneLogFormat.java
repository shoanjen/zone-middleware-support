package com.zone.middleware.log;

/**
 * ZoneLog 行格式。
 *
 * <p>TEXT 为人类可读的管道分隔段（本地开发默认）；JSON 为采集友好的单行 JSON 对象，
 * 字段固定齐备（未设置标识以 null 占位），供日志平台零解析规则采集。</p>
 */
public enum ZoneLogFormat {

    /** 管道分隔文本行（默认） */
    TEXT,

    /** 单行 JSON 对象（time/level 由 ZoneLog 自产） */
    JSON
}
