package com.zone.middleware.common.util;

import com.zone.middleware.common.entity.ZoneBrowserInfo;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.UserAgent;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 浏览器指纹工具：
 * <ul>
 *   <li>{@link #parseUserAgent(String)}：基于开源 UserAgentUtils 解析浏览器/系统/设备信息</li>
 *   <li>{@link #fingerprint(HttpServletRequest)}：基于请求头集合生成稳定的指纹标识（SHA-256）</li>
 * </ul>
 */
public final class ZoneBrowserUtils {

    /** 工具类禁止实例化 */
    private ZoneBrowserUtils() {
    }

    /**
     * 解析 User-Agent 字符串为结构化指纹信息。
     *
     * @param userAgent User-Agent 请求头字符串，可为 null 或空
     * @return 结构化指纹信息；空/非法输入返回全 Unknown 占位对象而不抛异常
     */
    public static ZoneBrowserInfo parseUserAgent(String userAgent) {
        ZoneBrowserInfo info = ZoneBrowserInfo.unknown();
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return info;
        }
        UserAgent ua = UserAgent.parseUserAgentString(userAgent);

        Browser browser = ua.getBrowser();
        if (browser != null && browser != Browser.UNKNOWN) {
            // group 归一化浏览器名（如 CHROME1xx → Chrome）
            info.setBrowser(browser.getGroup().getName());
            if (ua.getBrowserVersion() != null) {
                info.setBrowserVersion(ua.getBrowserVersion().getMajorVersion());
            }
        }
        if (ua.getOperatingSystem() != null) {
            info.setOs(ua.getOperatingSystem().getName());
            if (ua.getOperatingSystem().getDeviceType() != null) {
                info.setDeviceType(ua.getOperatingSystem().getDeviceType().getName());
            }
            info.setMobile(ua.getOperatingSystem().isMobileDevice());
        }
        return info;
    }

    /**
     * 基于请求头集合（User-Agent、Accept、Accept-Language）生成指纹标识。
     * <p>同一请求头集合生成相同标识，不同集合生成不同标识。</p>
     *
     * @param request 当前请求
     * @return SHA-256 十六进制指纹标识（64 个字符）
     */
    public static String fingerprint(HttpServletRequest request) {
        String userAgent = orEmpty(request.getHeader("User-Agent"));
        String accept = orEmpty(request.getHeader("Accept"));
        String acceptLanguage = orEmpty(request.getHeader("Accept-Language"));
        return sha256Hex(userAgent + "|" + accept + "|" + acceptLanguage);
    }

    /**
     * 空值归一为空字符串。
     *
     * @param value 原始值，可为 null
     * @return 去除首尾空白后的值，null 返回空串
     */
    private static String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 计算 SHA-256 摘要并输出十六进制小写。
     *
     * @param input 待摘要文本
     * @return 64 个十六进制字符
     * @throws IllegalStateException JDK 缺失 SHA-256 算法（理论不可达）
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，理论不可达
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
