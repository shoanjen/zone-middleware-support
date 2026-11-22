package com.zone.middleware.web;

import com.zone.middleware.common.constant.ZoneConstants;
import com.zone.middleware.log.ZoneTraceContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Trace 入站 Filter 行为测试：默认关零行为 / 开启后透传 / 无头生成 / 请求间无串号（清理验证）。
 */
class ZoneTraceInboundFilterTest {

    private static final String TRACE_PROP = "zone.middleware.web.trace-filter-enabled";

    @SpringBootTest(classes = FilterApp.class, properties = {
            TRACE_PROP + "=true",
            "zone.middleware.web.exception-advice-enabled=false"})
    @AutoConfigureMockMvc
    @Nested
    class EnabledTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void forwardedHeaderShouldBeReused() throws Exception {
            String body = mockMvc.perform(get("/trace/echo").header(ZoneConstants.HEADER_TRACE_ID, "t-20261122-0001"))
                    .andReturn().getResponse().getContentAsString();
            assertEquals(true, body.contains("traceId=t-20261122-0001"),
                    "Filter 执行中线程应绑定透传 traceId: " + body);
            assertEquals(true, body.contains("reqId=r-"), "reqId 应由 Filter 生成");
        }

        @Test
        void missingHeaderShouldGenerateNewTraceId() throws Exception {
            String first = mockMvc.perform(get("/trace/echo"))
                    .andReturn().getResponse().getHeader(ZoneConstants.HEADER_TRACE_ID);
            assertEquals(true, first != null && first.startsWith("t-"), "无头时应生成新 traceId: " + first);
        }

        @Test
        void sequentialRequestsShouldNotLeakContext() throws Exception {
            mockMvc.perform(get("/trace/echo").header(ZoneConstants.HEADER_TRACE_ID, "t-first-0001"));
            // 第二个请求（无头）：若上一请求未清理，MDC 残留会导致重复生成或串号
            String second = mockMvc.perform(get("/trace/echo"))
                    .andReturn().getResponse().getHeader(ZoneConstants.HEADER_TRACE_ID);
            assertEquals(true, second != null && !second.equals("t-first-0001"), "不应复用上一请求 traceId");
        }

        @Test
        void contextShouldBeClearedAfterRequest() throws Exception {
            // MockMvc 线程与测试线程同线程执行 Filter：请求结束后应清理
            mockMvc.perform(get("/trace/echo").header(ZoneConstants.HEADER_TRACE_ID, "t-clear-0001"));
            assertNull(ZoneTraceContext.getTraceId(), "请求结束后链路上下文应已清理");
            assertNull(ZoneTraceContext.getReqId(), "reqId 应同步清理");
        }
    }

    @SpringBootTest(classes = FilterApp.class, properties = {
            TRACE_PROP + "=false",
            "zone.middleware.web.exception-advice-enabled=false"})
    @AutoConfigureMockMvc
    @Nested
    class DisabledTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void disabledShouldNotRegisterFilter() throws Exception {
            mockMvc.perform(get("/trace/echo"))
                    .andExpect(result -> assertNull(result.getResponse()
                            .getHeader(ZoneConstants.HEADER_TRACE_ID), "默认关时不应回写链路头"));
        }
    }
}

@RestController
class TraceProbeController {

    @GetMapping("/trace/echo")
    public String echo() {
        return "traceId=" + ZoneTraceContext.getTraceId() + ",reqId=" + ZoneTraceContext.getReqId();
    }
}

@Configuration
@EnableAutoConfiguration
class FilterApp {

    @Bean
    TraceProbeController traceProbeController() {
        return new TraceProbeController();
    }
}
