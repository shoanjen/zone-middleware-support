package com.zone.middleware.web.filter;

import com.zone.middleware.common.constant.ZoneConstants;
import com.zone.middleware.log.ZoneTraceContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Trace 入站 Filter（可选，默认关）：请求入口绑定链路标识——请求头携带
 * {@code Zone-Trace-Id}（{@link ZoneConstants#HEADER_TRACE_ID}）时透传为 traceId
 * （串联上下游），缺失时生成新 traceId；reqId 每请求新生成（随机段长度经构造器配置，
 * 范围 8–32）；处理完成后（含异常）finally 清理，防止线程复用串号；响应头回写
 * traceId 供下游续链。
 */
public class ZoneTraceInboundFilter implements Filter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MIN_REQ_ID_LENGTH = 8;
    private static final int MAX_REQ_ID_LENGTH = 32;
    private static final int TRACE_ID_SEGMENT_LENGTH = 8;

    private final int reqIdLength;

    /**
     * 以默认 reqId 随机段长度（8）构造。
     */
    public ZoneTraceInboundFilter() {
        this(MIN_REQ_ID_LENGTH);
    }

    /**
     * 以指定 reqId 随机段长度构造。
     *
     * @param reqIdLength reqId 随机段长度（8–32）
     * @throws IllegalArgumentException 长度越界（消息含允许范围）
     */
    public ZoneTraceInboundFilter(int reqIdLength) {
        if (reqIdLength < MIN_REQ_ID_LENGTH || reqIdLength > MAX_REQ_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "reqId 随机段长度必须在 " + MIN_REQ_ID_LENGTH + "–" + MAX_REQ_ID_LENGTH + " 之间: " + reqIdLength);
        }
        this.reqIdLength = reqIdLength;
    }

    /**
     * 绑定链路标识后放行请求，出口清理（含异常路径）。
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤链
     * @throws IOException      链路透传的 IO 异常
     * @throws ServletException 链路透传的 Servlet 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest
                    && response instanceof HttpServletResponse httpResponse) {
                String traceId = httpRequest.getHeader(ZoneConstants.HEADER_TRACE_ID);
                if (traceId == null || traceId.trim().isEmpty()) {
                    traceId = newTraceId();
                }
                httpResponse.setHeader(ZoneConstants.HEADER_TRACE_ID, traceId);
                ZoneTraceContext.bind(traceId, newReqId());
            }
            chain.doFilter(request, response);
        } finally {
            ZoneTraceContext.clear();
        }
    }

    /**
     * 生成新 traceId（时间戳前缀 + 固定 8 位随机段，与手册 §5.3 样例风格一致）。
     *
     * @return 新 traceId
     */
    private String newTraceId() {
        return "t-" + LocalDateTime.now().format(STAMP) + "-" + randomSegment(TRACE_ID_SEGMENT_LENGTH);
    }

    /**
     * 生成新 reqId（r 前缀 + 配置长度的随机段）。
     *
     * @return 新 reqId
     */
    private String newReqId() {
        return "r-" + randomSegment(reqIdLength);
    }

    /**
     * 生成指定长度的随机段（UUID hex 共 32 位，覆盖 8–32 全部合法取值）。
     *
     * @param length 随机段长度
     * @return 随机串
     */
    private String randomSegment(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
