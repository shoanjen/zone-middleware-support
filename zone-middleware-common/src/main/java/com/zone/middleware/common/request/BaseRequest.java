package com.zone.middleware.common.request;

import com.zone.middleware.common.enums.OsType;
import com.zone.middleware.common.enums.Platform;
import lombok.Data;

import java.io.Serializable;

/**
 * Zone 生态基础请求载体。
 *
 * <p>上游应用的请求 DTO 继承本类后，统一携带请求标识、平台、IP、设备、系统、版本、
 * 时间戳字段，供网关 / 接口层做统一鉴权、审计与防重放校验；签名经请求头
 * {@code Zone-Access-Token} 承载（{@code BaseRequest} 不再包含签名字段）。</p>
 *
 * <p>{@code platform} 与 {@code os} 以字符串原样承载（透传客户端原文，未知值与大小写
 * 形态不做强转）；业务层需要规约时使用 {@link Platform#of} / {@link OsType#of} 解析：</p>
 *
 * <pre>{@code
 * public class OrderQueryRequest extends BaseRequest {
 *     private String orderNo;
 * }
 *
 * Platform platform = Platform.of(request.getPlatform()); // "web" → WEB；未知值 → null
 * OsType os = OsType.of(request.getOs());                 // "harmony" → HARMONY
 * }</pre>
 */
@Data
public class BaseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求唯一标识（用于链路追踪与幂等控制） */
    private String reqId;

    /** 请求平台原始值（协议小写：web / pc / mobile / pad；未知值原样保留，可用 {@link Platform#of} 解析） */
    private String platform;

    /** 客户端 IP 地址（可由 ZoneIpUtils.getClientIp 解析填充） */
    private String ip;

    /** 设备唯一标识 */
    private String deviceId;

    /** 操作系统原始值（协议小写：android / ios / windows / harmony / linux；未知值原样保留，可用 {@link OsType#of} 解析） */
    private String os;

    /** 接口版本号（如 1.0.0） */
    private String version;

    /** 接口请求时间戳（Epoch 毫秒；包装类型以区分「未提供」与 0） */
    private Long timestamp;
}
