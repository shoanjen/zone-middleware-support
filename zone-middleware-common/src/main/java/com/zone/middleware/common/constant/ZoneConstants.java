package com.zone.middleware.common.constant;

/**
 * Zone 生态顶层设计常量接口。
 *
 * <p>定位：Zone 二方库与上层应用的常量统一挂载点。上层应用按领域派生自己的常量接口
 * 并继承本接口（如 {@code public interface OrderConstants extends ZoneConstants {...}}），
 * 派生接口自动继承内置通用常量，避免魔法字符串在应用内散落。</p>
 *
 * <p>内置通用常量（当前为 Zone 协议请求头 Key）为后续二方包预留：
 * 鉴权二方包据此解析 {@link #HEADER_ACCESS_TOKEN}，日志链路二方包据此透传 {@link #HEADER_TRACE_ID}。</p>
 */
public interface ZoneConstants {

    /** 请求头 Key：接口签名（鉴权二方包约定，客户端按签名算法计算后经此 Key 携带） */
    String HEADER_ACCESS_TOKEN = "Zone-Access-Token";

    /** 请求头 Key：登录令牌（鉴权二方包约定，上游调用按此 Key 携带登录态 token） */
    String HEADER_AUTH_TOKEN = "Zone-Auth-Token";

    /** 请求头 Key：链路追踪 ID（日志二方包预留，服务间调用按此 Key 透传 traceId） */
    String HEADER_TRACE_ID = "Zone-Trace-Id";
}
