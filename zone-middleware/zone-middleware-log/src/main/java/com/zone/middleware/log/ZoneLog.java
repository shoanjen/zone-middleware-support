package com.zone.middleware.log;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * <p>行格式由 {@code zone.middleware.log.format} 选择：</p>
 * <ul>
 *   <li><b>TEXT</b>（默认）：段按固定顺序组成「应用名 | 环境 | traceId | reqId | 类名.方法名 | 消息」，
 *       段组成规则为「开关开启且值非空」，值缺席的段跳过、不产生空占位
 *       （时间与级别由底层日志框架 pattern 输出）。</li>
 *   <li><b>JSON</b>：每条输出为完整合法的单行 JSON 对象，字段固定为
 *       time（Epoch 毫秒）、level（级别名）、appName、env、traceId、reqId、class、method、
 *       message、exception；time 与 level 由 ZoneLog 自产，未设置的标识字段以 null 占位，
 *       携带异常时 exception 为完整堆栈字符串。建议底层 pattern 仅保留消息体与换行。</li>
 * </ul>
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
     * 统一日志出口：门槛过滤、异常剥离、行组装并委托底层日志框架。
     *
     * @param level      日志级别
     * @param className  埋点类名
     * @param methodName 埋点方法名
     * @param message    消息模板
     * @param args       占位符填充参数
     * @param throwable  显式异常（可为 null，null 时尝试从变参末位剥离）
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
        String line = render(level, className, methodName, fillPlaceholders(message, args), throwable);
        boolean json = ZoneLogSettings.current().getFormat() == ZoneLogFormat.JSON;
        if (json) {
            // 异常已内嵌于 JSON exception 字段，不再向底层框架重复传递堆栈
            switch (level) {
                case DEBUG -> logger.debug(line);
                case INFO -> logger.info(line);
                case WARN -> logger.warn(line);
                case ERROR -> logger.error(line);
            }
        } else {
            switch (level) {
                case DEBUG -> logger.debug(line);
                case INFO -> logger.info(line);
                case WARN -> logger.warn(line);
                case ERROR -> logger.error(line, throwable);
            }
        }
    }

    /**
     * 按当前行格式渲染整行：TEXT 为管道分隔段，JSON 为单行 JSON 对象。
     *
     * @param level      日志级别
     * @param className  埋点类名
     * @param methodName 埋点方法名
     * @param message    已填充占位符的消息
     * @param throwable  显式或末位剥离的异常，可为 null
     * @return 渲染后的整行文本
     */
    private static String render(ZoneLogLevel level, String className, String methodName,
                                 String message, Throwable throwable) {
        return ZoneLogSettings.current().getFormat() == ZoneLogFormat.JSON
                ? renderJson(level, className, methodName, message, throwable)
                : renderText(className, methodName, message);
    }

    /**
     * TEXT 行组装：段按固定顺序为「开关开启且值非空」者，值缺席的段跳过、不产生空占位。
     *
     * @param className  埋点类名
     * @param methodName 埋点方法名
     * @param message    已填充占位符的消息
     * @return 组装后的日志行文本
     */
    private static String renderText(String className, String methodName, String message) {
        ZoneLogSettings settings = ZoneLogSettings.current();
        StringBuilder sb = new StringBuilder(96);
        if (settings.isIncludeAppName() && notEmpty(settings.getAppName())) {
            sb.append(settings.getAppName());
        }
        if (notEmpty(settings.getEnv())) {
            appendSeparator(sb).append(settings.getEnv());
        }
        if (settings.isIncludeTraceId()) {
            String traceId = ZoneTraceContext.getTraceId();
            if (notEmpty(traceId)) {
                appendSeparator(sb).append(traceId);
            }
        }
        String reqId = ZoneTraceContext.getReqId();
        if (notEmpty(reqId)) {
            appendSeparator(sb).append(reqId);
        }
        if (notEmpty(className)) {
            appendSeparator(sb).append(className);
            if (notEmpty(methodName)) {
                sb.append('.').append(methodName);
            }
        } else if (notEmpty(methodName)) {
            appendSeparator(sb).append(methodName);
        }
        if (notEmpty(message)) {
            appendSeparator(sb).append(message);
        }
        return sb.toString();
    }

    /**
     * JSON 行组装：固定字段序 time → level → appName → env → traceId → reqId → class →
     * method → message → exception；time 与 level 自产，未设置标识 null 占位，
     * 携带异常时 exception 为完整堆栈字符串。序列化异常时回退 String.valueOf，日志输出永不中断。
     *
     * @param level      日志级别
     * @param className  埋点类名
     * @param methodName 埋点方法名
     * @param message    已填充占位符的消息
     * @param throwable  显式或末位剥离的异常，可为 null
     * @return 单行 JSON 文本
     */
    private static String renderJson(ZoneLogLevel level, String className, String methodName,
                                     String message, Throwable throwable) {
        ZoneLogSettings settings = ZoneLogSettings.current();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("time", System.currentTimeMillis());
        fields.put("level", level.name());
        fields.put("appName", settings.getAppName());
        fields.put("env", settings.getEnv());
        fields.put("traceId", ZoneTraceContext.getTraceId());
        fields.put("reqId", ZoneTraceContext.getReqId());
        fields.put("class", className);
        fields.put("method", methodName);
        fields.put("message", message);
        fields.put("exception", throwable == null ? null : stackTraceOf(throwable));
        try {
            return JSON.toJSONString(fields, JSONWriter.Feature.WriteMapNullValue);
        } catch (Exception fallback) {
            return String.valueOf(fields);
        }
    }

    /**
     * 捕获异常完整堆栈为字符串（JSON exception 字段内容）。
     *
     * @param throwable 待捕获异常
     * @return 含完整堆栈的字符串
     */
    private static String stackTraceOf(Throwable throwable) {
        StringWriter sw = new StringWriter(512);
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }

    /**
     * 判断文本非 null 且非空串。
     *
     * @param text 待判断文本
     * @return 非 null 且长度大于 0 时返回 true
     */
    private static boolean notEmpty(String text) {
        return text != null && !text.isEmpty();
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
     * 为非空前缀追加分隔符。
     *
     * @param sb 目标构建器
     * @return 同一构建器（链式）
     */
    private static StringBuilder appendSeparator(StringBuilder sb) {
        return sb.length() == 0 ? sb : sb.append(" | ");
    }
}
