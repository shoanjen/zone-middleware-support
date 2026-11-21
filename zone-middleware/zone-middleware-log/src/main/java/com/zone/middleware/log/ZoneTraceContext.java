package com.zone.middleware.log;

import org.slf4j.MDC;

/**
 * 链路双标识线程上下文工具：traceId（上下游串联标识）与 reqId（单请求标识），
 * 基于 SLF4J MDC 承载，同线程内所有 {@link ZoneLog} 输出自动携带。
 *
 * <p>两个标识均由调用方显式传入（本工具不提供生成器）；跨服务串联职责归 traceId
 * （出站传播头约定由 common 能力的 {@code ZoneConstants.HEADER_TRACE_ID} 承担），
 * reqId 不定义协议头传播。</p>
 */
public final class ZoneTraceContext {

    /** MDC 承载键名：链路串联标识 */
    public static final String MDC_KEY_TRACE_ID = "traceId";

    /** MDC 承载键名：单请求标识 */
    public static final String MDC_KEY_REQ_ID = "reqId";

    /** 工具类禁止实例化 */
    private ZoneTraceContext() {
    }

    /**
     * 组合设置当前线程双标识（空值忽略，后设置覆盖先设置）。
     *
     * @param traceId 链路串联标识，null 或空串时忽略
     * @param reqId   单请求标识，null 或空串时忽略
     */
    public static void bind(String traceId, String reqId) {
        setTraceId(traceId);
        setReqId(reqId);
    }

    /**
     * 设置当前线程 traceId（空值忽略，后设置覆盖先设置）。
     *
     * @param traceId 链路串联标识，null 或空串时忽略
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(MDC_KEY_TRACE_ID, traceId);
        }
    }

    /**
     * 获取当前线程 traceId。
     *
     * @return 已设置的 traceId；未设置返回 null
     */
    public static String getTraceId() {
        return MDC.get(MDC_KEY_TRACE_ID);
    }

    /**
     * 设置当前线程 reqId（空值忽略，后设置覆盖先设置）。
     *
     * @param reqId 单请求标识，null 或空串时忽略
     */
    public static void setReqId(String reqId) {
        if (reqId != null && !reqId.isEmpty()) {
            MDC.put(MDC_KEY_REQ_ID, reqId);
        }
    }

    /**
     * 获取当前线程 reqId。
     *
     * @return 已设置的 reqId；未设置返回 null
     */
    public static String getReqId() {
        return MDC.get(MDC_KEY_REQ_ID);
    }

    /** 清理当前线程双标识（线程复用前调用，避免串号）。 */
    public static void clear() {
        MDC.remove(MDC_KEY_TRACE_ID);
        MDC.remove(MDC_KEY_REQ_ID);
    }
}
