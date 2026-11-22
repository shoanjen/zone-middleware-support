package com.zone.middleware.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 入站支撑配置（前缀 zone.middleware.web；两个能力均默认关，显式开启才装配）。
 */
@Data
@ConfigurationProperties(prefix = "zone.middleware.web")
public class ZoneWebProperties {

    /** 全局异常处理器开关（@RestControllerAdvice，默认关） */
    private boolean exceptionAdviceEnabled = false;

    /** Trace 入站 Filter 开关（默认关） */
    private boolean traceFilterEnabled = false;

    /** reqId 随机段长度（8–32，越界启动失败；仅 trace-filter 开启时生效，traceId 段固定 8 位不受影响） */
    private int reqIdLength = 8;
}
