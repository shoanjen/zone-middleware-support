package com.zone.middleware.log;

import org.slf4j.MDC;

/**
 * traceId 线程上下文工具：基于 SLF4J MDC 承载，同线程内所有 {@link ZoneLog} 输出自动携带。
 */
public final class ZoneTraceContext {

    /** MDC 承载键名 */
    public static final String MDC_KEY = "traceId";

    /** 工具类禁止实例化 */
    private ZoneTraceContext() {
    }

    /**
     * 设置当前线程 traceId（空值忽略，后设置覆盖先设置）。
     *
     * @param traceId 跟踪标识，null 或空串时忽略
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    /**
     * 获取当前线程 traceId。
     *
     * @return 已设置的 traceId；未设置返回 null
     */
    public static String getTraceId() {
        return MDC.get(MDC_KEY);
    }

    /** 清理当前线程 traceId（线程复用前调用，避免串号）。 */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
