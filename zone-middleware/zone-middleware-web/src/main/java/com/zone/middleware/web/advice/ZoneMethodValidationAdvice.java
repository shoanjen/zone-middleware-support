package com.zone.middleware.web.advice;

import com.zone.middleware.common.error.BusinessError;
import com.zone.middleware.common.result.ZoneResult;
import com.zone.middleware.log.ZoneLog;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 方法级校验异常处理器（Spring 6.1+ / Boot 3.2+ 内建方法校验路径）：Controller 参数上直接标注
 * 约束注解（如 @NotBlank @RequestParam）时，Spring MVC 内建校验抛
 * {@link HandlerMethodValidationException}（早于经典 @Validated 拦截器路径），转 BIZ-4001
 * 含参数级消息。与 {@link ZoneGlobalExceptionAdvice} 同为低优先级、让位上游自定义 advice。
 *
 * <p>该异常类型仅存在于 Spring 6.1+（Boot 3.2+）：以独立类 + 装配条件隔离
 * （见 ZoneWebInboundAutoConfiguration），Boot 3.1 及以下不装配本 Bean、不内省本类；
 * Boot 3.0–3.1 的 @Validated 经典路径由 {@link ZoneGlobalExceptionAdvice} 的
 * ConstraintViolationException 处理器覆盖。</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ZoneMethodValidationAdvice {

    private static final String ADVICE_NAME = "ZoneMethodValidationAdvice";

    /**
     * 内建方法校验异常：转 BIZ-4001，message 取第一条校验失败（参数名 + 提示），
     * 格式与 BindException 字段路径一致。
     *
     * @param e 内建方法校验异常
     * @return BIZ-4001 失败返回体
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ZoneResult<Void> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        ZoneLog.warn(ADVICE_NAME, "handleHandlerMethodValidationException", "方法级校验失败: {}", e.getMessage());
        return ZoneResult.fail(BusinessError.PARAM_INVALID, firstParamMessage(e));
    }

    /**
     * 拼第一条参数校验消息：参数名 + 空格 + 提示（与 BindException 字段格式一致）。
     *
     * @param e 内建方法校验异常
     * @return 「参数名 提示」消息；无法解析时回退默认提示
     */
    private String firstParamMessage(HandlerMethodValidationException e) {
        if (e.getAllValidationResults() == null || e.getAllValidationResults().isEmpty()) {
            return BusinessError.PARAM_INVALID.getMessage();
        }
        var result = e.getAllValidationResults().get(0);
        String paramName = result.getMethodParameter().getParameterName();
        String defaultMessage = result.getResolvableErrors().isEmpty()
                ? BusinessError.PARAM_INVALID.getMessage()
                : result.getResolvableErrors().get(0).getDefaultMessage();
        return paramName == null ? defaultMessage : paramName + " " + defaultMessage;
    }
}
