package com.zone.middleware.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通用业务数据错误（参数、权限、数据状态等），码段前缀 BIZ-。
 */
@Getter
@RequiredArgsConstructor
public enum BusinessError implements ZoneError {

    /** 参数校验失败 */
    PARAM_INVALID("BIZ-4001", "参数校验失败"),

    /** 未授权 */
    UNAUTHORIZED("BIZ-4002", "未授权"),

    /** 禁止访问 */
    FORBIDDEN("BIZ-4003", "禁止访问"),

    /** 数据不存在 */
    DATA_NOT_FOUND("BIZ-4004", "数据不存在"),

    /** 数据冲突 */
    DATA_CONFLICT("BIZ-4005", "数据冲突"),

    /** 业务处理失败（通用兜底） */
    BUSINESS_FAILED("BIZ-5001", "业务处理失败");

    private final String code;
    private final String message;
}
