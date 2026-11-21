package com.zone.middleware.common.result;

import com.alibaba.fastjson2.JSON;
import com.zone.middleware.common.error.ZoneError;
import lombok.Data;

/**
 * Zone 生态统一服务返回体。
 * <p>字段：success / code / message / data / timestamp；JSON 序列化统一使用 fastjson2。</p>
 *
 * @param <T> 数据载荷类型
 */
@Data
public class ZoneResult<T> {

    /** 成功码 */
    public static final String SUCCESS_CODE = "SUCCESS";
    /** 成功默认消息 */
    public static final String SUCCESS_MESSAGE = "成功";

    private boolean success;
    private String code;
    private String message;
    private T data;
    private long timestamp;

    /** 反序列化用无参构造（fastjson2） */
    public ZoneResult() {
    }

    /**
     * 全参构造（工厂方法内部使用）。
     *
     * @param success 是否成功
     * @param code    返回码（成功码或错误码）
     * @param message 返回消息
     * @param data    数据载荷，可为 null
     */
    private ZoneResult(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构建成功结果（无数据）。
     *
     * @param <T> 数据载荷类型
     * @return success=true、code 为成功码、data 为 null 的返回体
     */
    public static <T> ZoneResult<T> ok() {
        return build(true, SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /**
     * 构建成功结果（携带数据）。
     *
     * @param data 数据载荷
     * @param <T>  数据载荷类型
     * @return success=true 且 data 为传入值的返回体
     */
    public static <T> ZoneResult<T> ok(T data) {
        return build(true, SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 构建失败结果（错误码与消息取自错误对象）。
     *
     * @param error 错误定义（SystemError / BusinessError）
     * @param <T>   数据载荷类型
     * @return success=false、code 与 message 取自错误对象的返回体
     */
    public static <T> ZoneResult<T> fail(ZoneError error) {
        return build(false, error.getCode(), error.getMessage(), null);
    }

    /**
     * 构建失败结果（错误码取自错误对象，消息覆盖为自定义内容）。
     *
     * @param error   错误定义（提供错误码）
     * @param message 覆盖错误对象默认描述的自定义消息
     * @param <T>     数据载荷类型
     * @return success=false、code 取自错误对象、message 为自定义消息的返回体
     */
    public static <T> ZoneResult<T> fail(ZoneError error, String message) {
        return build(false, error.getCode(), message, null);
    }

    /**
     * 组装返回体并打时间戳。
     *
     * @param success 是否成功
     * @param code    返回码
     * @param message 消息
     * @param data    数据载荷
     * @param <T>     数据载荷类型
     * @return 完整的统一返回体
     */
    private static <T> ZoneResult<T> build(boolean success, String code, String message, T data) {
        return new ZoneResult<>(success, code, message, data);
    }

    /**
     * 序列化为 JSON 字符串（fastjson2）。
     *
     * @return 当前返回体的 JSON 文本
     */
    public String toJson() {
        return JSON.toJSONString(this);
    }
}
