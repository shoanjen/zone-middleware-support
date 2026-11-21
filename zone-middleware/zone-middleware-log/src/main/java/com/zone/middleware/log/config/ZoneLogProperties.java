package com.zone.middleware.log.config;

import com.zone.middleware.log.ZoneLogFormat;
import com.zone.middleware.log.ZoneLogLevel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一日志配置（前缀 zone.middleware.log）。
 */
@Data
@ConfigurationProperties(prefix = "zone.middleware.log")
public class ZoneLogProperties {

    /** 应用名（缺省回退 spring.application.name，再回退 unknown） */
    private String appName;

    /** 环境标识（缺省回退 spring.profiles.active 首段，再回退 unknown） */
    private String env;

    /** 最低输出级别，默认 INFO */
    private ZoneLogLevel level = ZoneLogLevel.INFO;

    /** 输出是否包含 traceId 段 */
    private boolean includeTraceId = true;

    /** 输出是否包含应用名段 */
    private boolean includeAppName = true;

    /** 行格式 TEXT/JSON（非法值经枚举绑定导致启动失败），默认 TEXT */
    private ZoneLogFormat format = ZoneLogFormat.TEXT;
}
