package com.zone.middleware.web.advice;

import com.zone.middleware.common.error.BusinessError;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.common.result.ZoneResult;
import com.zone.middleware.log.ZoneLog;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;

/**
 * 全局异常处理器（可选，默认关）：将四类异常统一转换为 {@link ZoneResult} 返回体——
 * {@link ZoneBusinessException} 按原错误码透传、参数校验异常（BindException /
 * MethodArgumentNotValidException，@RequestBody/表单绑定）转 BIZ-4001 含字段级消息、
 * 方法级校验异常（ConstraintViolationException，类上 @Validated + 参数级约束）同样转
 * BIZ-4001（参数路径 + 提示）、其余未捕获异常兜底 SYS-9999（通用提示，堆栈仅落服务端
 * 日志，不向调用方泄露内部细节）。
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)}：Spring 按 Order 升序挑选匹配的 advice，
 * 最低优先级保证上游自定义 advice（默认顺序或更小 Order）优先命中，本处理器仅兜底。
 * 上游 advice 需压过本处理器时显式声明更小 Order 值即可。</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ZoneGlobalExceptionAdvice {

    private static final String ADVICE_NAME = "ZoneGlobalExceptionAdvice";

    /**
     * 业务异常：按原错误码与消息透传为失败返回体。
     *
     * @param e 业务异常
     * @return 携带原错误码与消息的失败 ZoneResult
     */
    @ExceptionHandler(ZoneBusinessException.class)
    public ZoneResult<Void> handleZoneBusinessException(ZoneBusinessException e) {
        return ZoneResult.fail(e.getError(), e.getMessage());
    }

    /**
     * 参数校验异常（@RequestBody 校验失败 / 表单绑定校验失败）：转 BIZ-4001，
     * message 取首个字段校验错误（字段名 + 提示）。
     *
     * @param e 校验异常
     * @return BIZ-4001 失败返回体
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ZoneResult<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? BusinessError.PARAM_INVALID.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ZoneResult.fail(BusinessError.PARAM_INVALID, message);
    }

    /**
     * 方法级校验异常（类上 @Validated + 参数级约束）：转 BIZ-4001，
     * message 取第一条违规（参数路径 + 提示），格式与 BindException 路径一致。
     *
     * @param e 方法级校验异常
     * @return BIZ-4001 失败返回体
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ZoneResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String message = violations == null || violations.isEmpty()
                ? BusinessError.PARAM_INVALID.getMessage()
                : firstViolationMessage(violations);
        return ZoneResult.fail(BusinessError.PARAM_INVALID, message);
    }

    /**
     * 兜底异常：统一 SYS-9999 通用提示，堆栈仅落服务端日志。
     *
     * @param e 未捕获异常
     * @return SYS-9999 失败返回体（通用提示，不携带内部细节）
     */
    @ExceptionHandler(Exception.class)
    public ZoneResult<Void> handleUnexpectedException(Exception e) {
        ZoneLog.error(ADVICE_NAME, "handleUnexpectedException", "未捕获异常兜底: {}", e, e.getMessage());
        return ZoneResult.fail(SystemError.INTERNAL_ERROR, "系统繁忙，请稍后重试");
    }

    /**
     * 拼第一条违规消息：参数路径 + 空格 + 提示（与 BindException 字段格式一致）。
     *
     * @param violations 违规集合（非空）
     * @return 第一条违规的「路径 提示」消息
     */
    private String firstViolationMessage(Set<ConstraintViolation<?>> violations) {
        ConstraintViolation<?> violation = violations.iterator().next();
        return violation.getPropertyPath() + " " + violation.getMessage();
    }
}
