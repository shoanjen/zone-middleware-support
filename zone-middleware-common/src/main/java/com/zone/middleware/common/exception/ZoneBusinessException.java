package com.zone.middleware.common.exception;

import com.zone.middleware.common.error.ZoneError;
import lombok.Getter;

/**
 * Zone 生态统一业务异常：携带 {@link ZoneError}，构造时可覆盖默认描述。
 */
@Getter
public class ZoneBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 携带的错误定义（标记 transient，异常不参与 JSON 序列化） */
    private final transient ZoneError error;

    /**
     * 以错误对象的默认描述构造异常。
     *
     * @param error 携带的错误定义，不能为空
     */
    public ZoneBusinessException(ZoneError error) {
        super(error.getMessage());
        this.error = error;
    }

    /**
     * 以自定义消息构造异常。
     *
     * @param error   携带的错误定义（提供错误码）
     * @param message 覆盖默认描述的异常消息
     */
    public ZoneBusinessException(ZoneError error, String message) {
        super(message);
        this.error = error;
    }

    /**
     * 以自定义消息与根因构造异常。
     *
     * @param error   携带的错误定义（提供错误码）
     * @param message 覆盖默认描述的异常消息
     * @param cause   异常根因
     */
    public ZoneBusinessException(ZoneError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }
}
