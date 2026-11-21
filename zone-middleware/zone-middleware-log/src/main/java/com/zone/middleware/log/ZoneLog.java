package com.zone.middleware.log;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Zone 生态统一日志门面（无 AOP，显式调用埋点）。
 *
 * <p>入参顺序：<b>日志级别、类名、方法名、消息、可变参数</b>。消息支持 SLF4J 风格的
 * {@code {}} 占位符，由可变参数按出现顺序填充——彻底避免字符串 {@code +} 拼接：</p>
 *
 * <pre>{@code
 * ZoneLog.info("OrderService", "createOrder", "订单{}创建成功,金额{}", orderId, amount);
 * ZoneLog.info("OrderService", "createOrder", "用户数据: {}", "{\"orderId\":123}");  // JSON 字符串原样透传
 * ZoneLog.info("OrderService", "createOrder", "上下文: {}", ctxMap);                 // 对象自动 fastjson2 JSON
 * ZoneLog.error("OrderService", "save", "订单{}保存失败", orderId, exception);       // 末位异常自动输出堆栈
 * }</pre>
 *
 * <p>占位符渲染规则：</p>
 * <ul>
 *   <li>字符串：原样输出（JSON 字符串直接透传）</li>
 *   <li>数值 / 布尔 / 字符：按字面值输出</li>
 *   <li>其他对象（Map / POJO / 集合 / 数组）：fastjson2 JSON 输出</li>
 *   <li>null：输出 {@code null}</li>
 *   <li>多于占位符的参数：忽略</li>
 *   <li>末位未被占位符消费的 {@code Throwable}：作为异常堆栈输出（SLF4J 惯例）</li>
 * </ul>
 *
 * <p>需要无条件输出堆栈时使用显式 {@code Throwable} 重载（显式重载在调用点优先匹配，
 * 如需把异常对象渲染进占位符，请传 {@code e.toString()}）。</p>
 *
 * <p>输出格式：{@code 时间 | 级别 | 应用名 | traceId | 类名.方法名 | 消息}
 * （时间与级别由底层日志框架 pattern 输出；应用名 / traceId 可配置开关）。</p>
 */
public final class ZoneLog {

    /** 工具类禁止实例化 */
    private ZoneLog() {
    }

    // ==================== 通用（显式级别） ====================

    /**
     * 以显式级别输出日志（消息占位符 + 变参填充）。
     *
     * @param level      日志级别，null 时静默忽略
     * @param className  埋点类名（同时作为底层 SLF4J logger 名），null/空时回退到 ZoneLog
     * @param methodName 埋点方法名，null/空时省略方法段
     * @param message    消息模板，支持 {@code {}} 占位符
     * @param args       占位符填充参数；末位未被消费的 Throwable 自动作为异常堆栈
     */
    public static void log(ZoneLogLevel level, String className, String methodName,
                           String message, Object... args) {
        doLog(level, className, methodName, message, args, null);
    }

    /**
     * 以显式级别输出日志（显式异常堆栈 + 消息占位符 + 变参填充）。
     *
     * @param level      日志级别，null 时静默忽略
     * @param className  埋点类名（同时作为底层 SLF4J logger 名）
     * @param methodName 埋点方法名
     * @param message    消息模板，支持 {@code {}} 占位符
     * @param throwable  无条件输出的异常堆栈
     * @param args       占位符填充参数（不影响显式异常的堆栈输出）
     */
    public static void log(ZoneLogLevel level, String className, String methodName,
                           String message, Throwable throwable, Object... args) {
        doLog(level, className, methodName, message, args, throwable);
    }

    // ==================== DEBUG ====================

    /**
     * DEBUG 级别输出（消息占位符 + 变参填充）。
     *
     * @param className  埋点类名（同时作为底层 SLF4J logger 名）
     * @param methodName 埋点方法名
     * @param message    消息模板，支持 {@code {}} 占位符
     * @param args       占位符填充参数
     */
    public static void debug(String className, String methodName, String message, Object... args) {
        doLog(ZoneLogLevel.DEBUG, className, methodName, message, args, null);
    }

    // ==================== INFO ====================

    /**
     * INFO 级别输出（消息占位符 + 变参填充）。
     *
     * @param className  埋点类名（同时作为底层 SLF4J logger 名）
     * @param methodName 埋点方法名
     * @param message    消息模板，支持 {@code {}} 占位符
     * @param args       占位符填充参数
     */
    public static void info(String className, String methodName, String message, Object... args) {
        doLog(ZoneLogLevel.INFO, className, methodName, message, args, null);
    }

    // ==================== WARN ====================

    /**
     * WARN 级别输出（消息占位符 + 变参填充）。
     *
     * @param className  埋点类名（同时作为底层 SLF4J logger 名）
     * @param methodName 埋点方法名
     * @param message    消息模板，支持 {@code {}} 占位符
     * @param args       占位符填充参数
     */
    public static void warn(String className, String methodName, String message, Object... args) {
        doLog(ZoneLogLevel.WARN, className, methodName, message, args, null);
    }

    // ==================== ERROR ====================

    /**
     * ERROR 级别输出（消息占位符 + 变参填充；末位未消费的 Throwable 自动作为堆栈）。
     *
     * @param className  埋点类名（同时作为底层 SLF4J logger 名）
     * @param methodName 埋点方法名
     * @param message    消息模板，支持 {@code {}} 占位符
     * @param args       占位符填充参数；末位未被消费的 Throwable 自动作为异常堆栈
     */
    public static void error(String className, String methodName, String message, Object... args) {
        doLog(ZoneLogLevel.ERROR, className, methodName, message, args, null);
    }

    /**
     * ERROR 级别输出（显式异常堆栈 + 消息占位符 + 变参填充）。
     *
     * @param className  埋点类名（同时作为底层 SLF4J logger 名）
     * @param methodName 埋点方法名
     * @param message    消息模板，支持 {@code {}} 占位符
     * @param throwable  无条件输出的异常堆栈
     * @param args       占位符填充参数（不影响显式异常的堆栈输出）
     */
    public static void error(String className, String methodName, String message,
                             Throwable throwable, Object... args) {
        doLog(ZoneLogLevel.ERROR, className, methodName, message, args, throwable);
    }

    // ==================== 内部实现 ====================

    /**
     * 统一日志出口：门槛过滤、异常剥离、格式组装并委托底层日志框架。
     *
     * @param level     日志级别
     * @param className 埋点类名
     * @param methodName 埋点方法名
     * @param message   消息模板
     * @param args      占位符填充参数
     * @param throwable 显式异常（可为 null，null 时尝试从变参末位剥离）
     */
    private static void doLog(ZoneLogLevel level, String className, String methodName,
                              String message, Object[] args, Throwable throwable) {
        if (level == null || level.rank() < ZoneLogSettings.current().getLevel().rank()) {
            return;
        }
        if (throwable == null) {
            throwable = detachTrailingThrowable(message, args);
            if (throwable != null) {
                args = Arrays.copyOf(args, args.length - 1);
            }
        }
        Logger logger = LoggerFactory.getLogger(
                className == null || className.isEmpty() ? ZoneLog.class.getName() : className);
        String line = format(className, methodName, fillPlaceholders(message, args));
        switch (level) {
            case DEBUG -> logger.debug(line);
            case INFO -> logger.info(line);
            case WARN -> logger.warn(line);
            case ERROR -> logger.error(line, throwable);
        }
    }

    /**
     * 末位参数为 Throwable 且占位符不足以消费它时，将其作为异常返回（SLF4J 惯例）。
     *
     * @param message 消息模板
     * @param args    变参数组
     * @return 应转为堆栈的末位异常；无需剥离时返回 null
     */
    private static Throwable detachTrailingThrowable(String message, Object[] args) {
        if (args.length > 0 && args[args.length - 1] instanceof Throwable throwable
                && countPlaceholders(message) < args.length) {
            return throwable;
        }
        return null;
    }

    /**
     * 将消息中的 {@code {}} 按序替换为参数渲染结果；多余参数忽略。
     *
     * @param message 消息模板
     * @param args    填充参数
     * @return 填充后的完整消息
     */
    static String fillPlaceholders(String message, Object[] args) {
        if (message == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message.length() + 32);
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            char c = message.charAt(i);
            if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}'
                    && argIndex < args.length) {
                sb.append(renderArg(args[argIndex++]));
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * 渲染单个占位符参数：字符串原样（JSON 透传）、字面值类型直出、其他对象 fastjson2 JSON。
     *
     * @param arg 待渲染参数，可为 null
     * @return 渲染后的文本
     */
    private static String renderArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof String text) {
            return text;
        }
        if (arg instanceof Number || arg instanceof Boolean || arg instanceof Character) {
            return String.valueOf(arg);
        }
        if (arg instanceof Throwable throwable) {
            return throwable.toString();
        }
        return JSON.toJSONString(arg);
    }

    /**
     * 统计消息中非重叠 {@code {}} 占位符数量。
     *
     * @param message 消息模板，可为 null
     * @return 占位符个数
     */
    private static int countPlaceholders(String message) {
        if (message == null) {
            return 0;
        }
        int count = 0;
        int i = 0;
        while (i < message.length() - 1) {
            if (message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                count++;
                i += 2;
            } else {
                i++;
            }
        }
        return count;
    }

    /**
     * 组装日志行：应用名 | traceId | 类名.方法名 | 消息（按配置开关裁剪段）。
     *
     * @param className  埋点类名
     * @param methodName 埋点方法名
     * @param message    已填充占位符的消息
     * @return 组装后的日志行文本
     */
    private static String format(String className, String methodName, String message) {
        ZoneLogSettings settings = ZoneLogSettings.current();
        StringBuilder sb = new StringBuilder(96);
        if (settings.isIncludeAppName()) {
            sb.append(settings.getAppName());
        }
        if (settings.isIncludeTraceId()) {
            String traceId = ZoneTraceContext.getTraceId();
            appendSeparator(sb).append(traceId == null ? "" : traceId);
        }
        appendSeparator(sb).append(className == null ? "" : className);
        if (methodName != null && !methodName.isEmpty()) {
            sb.append('.').append(methodName);
        }
        if (message != null && !message.isEmpty()) {
            appendSeparator(sb).append(message);
        }
        return sb.toString();
    }

    /**
     * 为非空前缀追加分隔符。
     *
     * @param sb 目标构建器
     * @return 同一构建器（链式）
     */
    private static StringBuilder appendSeparator(StringBuilder sb) {
        return sb.length() == 0 ? sb : sb.append(" | ");
    }
}
