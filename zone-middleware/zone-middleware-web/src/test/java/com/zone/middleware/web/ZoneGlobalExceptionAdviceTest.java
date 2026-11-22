package com.zone.middleware.web;

import com.zone.middleware.common.error.BusinessError;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.common.result.ZoneResult;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.annotation.Validated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器行为测试：默认关零行为 / 开启后三类异常转统一返回体 / 让位上游自定义 advice。
 */
class ZoneGlobalExceptionAdviceTest {

    private static final String PROP = "zone.middleware.web.exception-advice-enabled";

    /**
     * 经典 @Validated 拦截器路径（Boot 3.0–3.1 抛 ConstraintViolationException；3.2+ MVC 内建校验
     * 抛 HandlerMethodValidationException 不再走此 handler）——直接单测 handler 的格式化逻辑。
     */
    @Test
    void constraintViolationHandlerShouldFormatFirstViolation() {
        jakarta.validation.Validator validator =
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var bean = new Object() {
            @jakarta.validation.constraints.NotBlank(message = "不能为空") String orderNo = "";
        };
        var violations = validator.validate(bean);
        var exception = new jakarta.validation.ConstraintViolationException(violations);
        ZoneResult<Void> result = new com.zone.middleware.web.advice.ZoneGlobalExceptionAdvice()
                .handleConstraintViolationException(exception);
        assertEquals(BusinessError.PARAM_INVALID.getCode(), result.getCode());
        assertEquals(true, result.getMessage().contains("orderNo"), "应含字段路径: " + result.getMessage());
        assertEquals(true, result.getMessage().contains("不能为空"), "应含约束消息: " + result.getMessage());
    }

    @SpringBootTest(classes = AdviceApp.class, properties = {
            PROP + "=true",
            "zone.middleware.web.trace-filter-enabled=false"})
    @AutoConfigureMockMvc
    @Nested
    class EnabledTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void businessExceptionShouldCarryOriginalCodeAndMessage() throws Exception {
            String body = mockMvc.perform(get("/advice/biz")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ZoneResult<?> r = com.alibaba.fastjson2.JSON.parseObject(body, ZoneResult.class);
            assertEquals(BusinessError.DATA_NOT_FOUND.getCode(), r.getCode(), "应透传业务错误码");
            assertEquals("handled-by-upstream", r.getMessage(), "上游 advice（更小 Order）应优先命中");
        }

        @Test
        void validationExceptionShouldMapToParamInvalidWithFieldMessage() throws Exception {
            String body = mockMvc.perform(post("/advice/valid")
                            .contentType("application/json")
                            .content("{\"orderNo\":\"\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ZoneResult<?> r = com.alibaba.fastjson2.JSON.parseObject(body, ZoneResult.class);
            assertEquals(BusinessError.PARAM_INVALID.getCode(), r.getCode(), "校验失败应转 BIZ-4001");
            assertEquals(true, r.getMessage().contains("orderNo"), "message 应含字段级信息: " + r.getMessage());
        }

        @Test
        void methodLevelValidationShouldMapToParamInvalidWithParamPath() throws Exception {
            // Boot 3.2+：Spring MVC 内建方法校验抛 HandlerMethodValidationException（由 ZoneMethodValidationAdvice 接管）
            String body = mockMvc.perform(get("/advice/param").param("orderNo", ""))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ZoneResult<?> r = com.alibaba.fastjson2.JSON.parseObject(body, ZoneResult.class);
            assertEquals(BusinessError.PARAM_INVALID.getCode(), r.getCode(),
                    "方法级校验异常应转 BIZ-4001 而非兜底 SYS-9999");
            assertEquals(true, r.getMessage().contains("orderNo"), "message 应含参数路径: " + r.getMessage());
        }

        @Test
        void methodLevelValidationWithMultipleViolationsShouldTakeFirst() throws Exception {
            String body = mockMvc.perform(get("/advice/param2").param("a", "").param("b", ""))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ZoneResult<?> r = com.alibaba.fastjson2.JSON.parseObject(body, ZoneResult.class);
            assertEquals(BusinessError.PARAM_INVALID.getCode(), r.getCode());
            assertEquals(true, r.getMessage().contains("a") || r.getMessage().contains("b"),
                    "多违规时取第一条（含参数路径）: " + r.getMessage());
        }

        @Test
        void unexpectedExceptionShouldFallBackToInternalErrorWithoutLeakingDetails() throws Exception {
            String body = mockMvc.perform(get("/advice/unexpected")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ZoneResult<?> r = com.alibaba.fastjson2.JSON.parseObject(body, ZoneResult.class);
            assertEquals(SystemError.INTERNAL_ERROR.getCode(), r.getCode(), "兜底应转 SYS-9999");
            assertEquals(false, r.getMessage().contains("DB 密码泄露样例"), "兜底响应不得携带内部细节");
        }
    }

    @SpringBootTest(classes = AdviceApp.class, properties = {
            "zone.middleware.web.exception-advice-enabled=false",
            "zone.middleware.web.trace-filter-enabled=false"})
    @AutoConfigureMockMvc
    @Nested
    class DisabledTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void disabledShouldKeepNativeErrorBehavior() {
            // 未开启时不装配 advice：MockMvc 下未捕获异常直接穿透（MockMvc 不经容器 /error 转发），
            // 证明异常未被统一返回体接管
            try {
                mockMvc.perform(get("/advice/unexpected"));
                throw new AssertionError("未开启 advice 时异常应穿透而非被转成统一返回体");
            } catch (Exception thrown) {
                // MockMvc 会将未处理异常包装为 ServletException：按根因断言穿透
                Throwable root = thrown;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                assertEquals(IllegalStateException.class, root.getClass(),
                        "根因应为原始 IllegalStateException（未被统一返回体接管）");
            }
        }
    }
}

@Validated
@RestController
class AdviceProbeController {

    @GetMapping("/advice/biz")
    public String biz() {
        throw new ZoneBusinessException(BusinessError.DATA_NOT_FOUND, "订单不存在样例");
    }

    /** 方法级校验：单参数违规 */
    @GetMapping("/advice/param")
    public String param(@org.springframework.web.bind.annotation.RequestParam("orderNo")
                        @jakarta.validation.constraints.NotBlank(message = "不能为空") String orderNo) {
        return "ok";
    }

    /** 方法级校验：多参数违规（断言取第一条） */
    @GetMapping("/advice/param2")
    public String param2(@org.springframework.web.bind.annotation.RequestParam("a")
                         @jakarta.validation.constraints.NotBlank(message = "不能为空") String a,
                         @org.springframework.web.bind.annotation.RequestParam("b")
                         @jakarta.validation.constraints.NotBlank(message = "不能为空") String b) {
        return "ok";
    }

    @PostMapping("/advice/valid")
    public String valid(@Validated @RequestBody AdviceOrderReq req) {
        return "ok";
    }

    @GetMapping("/advice/unexpected")
    public String unexpected() {
        throw new IllegalStateException("DB 密码泄露样例");
    }
}

class AdviceOrderReq {

    @NotBlank(message = "不能为空")
    private String orderNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}

/** 上游自定义 advice：更小 Order，验证优先命中 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class UpstreamBizAdvice {

    @ExceptionHandler(ZoneBusinessException.class)
    public ZoneResult<Void> handle(ZoneBusinessException e) {
        return ZoneResult.fail(e.getError(), "handled-by-upstream");
    }
}

@Configuration
@EnableAutoConfiguration
class AdviceApp {

    @Bean
    AdviceProbeController adviceProbeController() {
        return new AdviceProbeController();
    }

    @Bean
    UpstreamBizAdvice upstreamBizAdvice() {
        return new UpstreamBizAdvice();
    }
}
