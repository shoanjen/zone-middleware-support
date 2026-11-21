package com.zone.middleware.common.entity;

import lombok.Data;

/**
 * 浏览器指纹信息（由 {@link com.zone.middleware.common.util.ZoneBrowserUtils#parseUserAgent(String)} 解析得到）。
 */
@Data
public class ZoneBrowserInfo {

    private String browser = "Unknown";
    private String browserVersion = "unknown";
    private String os = "Unknown";
    private String osVersion = "unknown";
    private String deviceType = "Unknown";
    private boolean mobile = false;

    /**
     * 全未知占位对象。
     *
     * @return 各字段为 Unknown/unknown 占位值的实例
     */
    public static ZoneBrowserInfo unknown() {
        return new ZoneBrowserInfo();
    }
}
