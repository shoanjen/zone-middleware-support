package com.zone.middleware.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 系统错误（基础设施、中间件、框架层面的错误），码段前缀 SYS-。
 */
@Getter
@RequiredArgsConstructor
public enum SystemError implements ZoneError {

    /** 配置错误 */
    CONFIG_ERROR("SYS-1001", "配置错误"),

    /** 数据库访问错误 */
    DB_ERROR("SYS-2001", "数据库访问错误"),

    /** Redis 访问错误 */
    REDIS_ERROR("SYS-2002", "Redis访问错误"),

    /** HTTP 请求失败 */
    HTTP_ERROR("SYS-3001", "HTTP请求失败"),

    /** 服务暂不可用 */
    SERVICE_UNAVAILABLE("SYS-3003", "服务暂不可用"),

    /** 系统内部错误 */
    INTERNAL_ERROR("SYS-9999", "系统内部错误");

    private final String code;
    private final String message;
}
