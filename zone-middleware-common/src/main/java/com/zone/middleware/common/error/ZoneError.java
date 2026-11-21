package com.zone.middleware.common.error;

/**
 * Zone 生态顶层错误接口。
 * <p>有两个子类型实现：{@link SystemError}（系统错误）与 {@link BusinessError}（通用业务数据错误）。</p>
 */
public interface ZoneError {

    /**
     * 获取错误码。
     * <p>系统错误以 {@code SYS-} 开头，业务错误以 {@code BIZ-} 开头，两码段互不重叠。</p>
     *
     * @return 错误码字符串
     */
    String getCode();

    /**
     * 获取错误默认描述。
     *
     * @return 供人阅读的错误描述
     */
    String getMessage();
}
