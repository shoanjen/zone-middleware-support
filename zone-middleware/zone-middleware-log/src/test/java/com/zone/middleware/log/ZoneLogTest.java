package com.zone.middleware.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneLogTest {

    private static final String CLASS_NAME = "zone.test.OrderService";

    private ListAppender<ILoggingEvent> appender;
    private Logger logbackLogger;

    @BeforeEach
    void setUp() {
        ZoneLogSettings.reset();
        ZoneTraceContext.clear();
        logbackLogger = (Logger) LoggerFactory.getLogger(CLASS_NAME);
        // 保证 test 也能输出 debug
        logbackLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        ZoneLogSettings.reset();
        ZoneTraceContext.clear();
    }

    @Test
    void fourLevelsShouldDelegateToSlf4j() {
        ZoneLogSettings.apply("app", ZoneLogLevel.DEBUG, true, true, "unknown", ZoneLogFormat.TEXT);
        ZoneLog.debug(CLASS_NAME, "create", "debug-msg");
        ZoneLog.info(CLASS_NAME, "create", "info-msg");
        ZoneLog.warn(CLASS_NAME, "create", "warn-msg");
        ZoneLog.error(CLASS_NAME, "create", "error-msg");

        List<ILoggingEvent> events = appender.list;
        assertEquals(4, events.size());
        assertEquals(Level.DEBUG, events.get(0).getLevel());
        assertEquals(Level.INFO, events.get(1).getLevel());
        assertEquals(Level.WARN, events.get(2).getLevel());
        assertEquals(Level.ERROR, events.get(3).getLevel());
        assertTrue(events.get(1).getFormattedMessage().contains("info-msg"));
    }

    @Test
    void placeholdersShouldBeFilledByArgsInOrder() {
        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}创建成功,金额{}", "OID-1", 99.9);

        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("订单OID-1创建成功,金额99.9"),
                "占位符应按序填充: " + message);
    }

    @Test
    void pojoArgShouldRenderAsJson() {
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("orderId", 123);
        biz.put("amount", 99.9);

        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}创建成功", biz);

        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("\"orderId\":123"), "对象参数应渲染 JSON: " + message);
        assertTrue(message.contains("\"amount\":99.9"));
    }

    @Test
    void jsonStringArgShouldPassThrough() {
        ZoneLog.info(CLASS_NAME, "createOrder", "用户数据: {}", "{\"orderId\":123}");

        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("用户数据: {\"orderId\":123}"),
                "JSON 字符串参数应原样透传: " + message);
    }

    @Test
    void nullArgShouldRenderNull() {
        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}处理", (Object) null);

        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("订单null处理"), "null 参数应渲染为 null: " + message);
    }

    @Test
    void extraArgsShouldBeIgnored() {
        ZoneLog.info(CLASS_NAME, "create", "msg{}", "a", "b");

        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.endsWith("msga"), "多余参数应被忽略: " + message);
    }

    @Test
    void trailingThrowableShouldBecomeStackTrace() {
        ZoneLog.error(CLASS_NAME, "save", "订单{}保存失败", "OID-9",
                new IllegalStateException("boom"));

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("订单OID-9保存失败"));
        assertEquals("java.lang.IllegalStateException", event.getThrowableProxy().getClassName());
    }

    @Test
    void explicitThrowableOverloadShouldAlwaysAttachStack() {
        ZoneLog.error(CLASS_NAME, "save", "订单{}保存失败",
                new IllegalStateException("boom2"), "OID-8");

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("订单OID-8保存失败"),
                "显式异常重载中变参仍填充占位符: " + event.getFormattedMessage());
        assertEquals("java.lang.IllegalStateException", event.getThrowableProxy().getClassName());
    }

    @Test
    void formatShouldContainSegmentsInOrder() {
        ZoneLogSettings.apply("order-service", ZoneLogLevel.INFO, true, true, "dev", ZoneLogFormat.TEXT);
        ZoneTraceContext.bind("trace-001", "req-001");

        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}创建成功", "OID-1");

        String message = appender.list.get(0).getFormattedMessage();
        int appIdx = message.indexOf("order-service");
        int envIdx = message.indexOf("dev");
        int traceIdx = message.indexOf("trace-001");
        int reqIdx = message.indexOf("req-001");
        int methodIdx = message.indexOf(CLASS_NAME + ".createOrder");
        int msgIdx = message.indexOf("订单OID-1创建成功");
        assertTrue(appIdx >= 0 && envIdx > appIdx && traceIdx > envIdx && reqIdx > traceIdx
                        && methodIdx > reqIdx && msgIdx > methodIdx,
                "字段顺序应为 应用名|环境|traceId|reqId|类名.方法名|消息: " + message);
    }

    @Test
    void absentSegmentsShouldBeSkippedWithoutEmptySlot() {
        // 默认设置（appName=unknown, env=unknown），traceId 与 reqId 均未设置
        ZoneLog.info(CLASS_NAME, "create", "plain-msg");

        String message = appender.list.get(0).getFormattedMessage();
        assertEquals("unknown | unknown | " + CLASS_NAME + ".create | plain-msg",
                message, "缺席段应跳过且无空占位: " + message);
    }

    @Test
    void envSegmentShouldAlwaysExist() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, true, true, "prod", ZoneLogFormat.TEXT);

        ZoneLog.info(CLASS_NAME, "create", "msg");

        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.startsWith("app | prod | "), "环境段应始终存在且紧跟应用名: " + message);
    }

    @Test
    void reqIdShouldBeCarriedInTextAndCleared() {
        ZoneTraceContext.setReqId("r-100");

        ZoneLog.info(CLASS_NAME, "create", "with-req");
        String first = appender.list.get(0).getFormattedMessage();
        assertEquals("unknown | unknown | r-100 | " + CLASS_NAME + ".create | with-req",
                first, "reqId 段应位于 traceId 段之后、类名段之前: " + first);

        ZoneTraceContext.clear();
        ZoneLog.info(CLASS_NAME, "create", "without-req");
        String second = appender.list.get(1).getFormattedMessage();
        assertEquals("unknown | unknown | " + CLASS_NAME + ".create | without-req",
                second, "清理后不应携带 reqId: " + second);
    }

    @Test
    void defaultLevelThresholdShouldBlockDebug() {
        // 默认 INFO 门槛
        ZoneLog.debug(CLASS_NAME, "create", "should-not-appear");
        ZoneLog.info(CLASS_NAME, "create", "should-appear");
        assertEquals(1, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("should-appear"));
    }

    @Test
    void loweredThresholdShouldAllowDebug() {
        ZoneLogSettings.apply("app", ZoneLogLevel.DEBUG, true, true, "unknown", ZoneLogFormat.TEXT);
        ZoneLog.debug(CLASS_NAME, "create", "debug-now-visible");
        assertEquals(1, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("debug-now-visible"));
    }

    @Test
    void traceIdShouldBeCarriedAndCleared() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, true, true, "unknown", ZoneLogFormat.TEXT);

        ZoneTraceContext.setTraceId("t-100");
        ZoneLog.info(CLASS_NAME, "create", "with-trace");
        String first = appender.list.get(0).getFormattedMessage();
        assertEquals("app | unknown | t-100 | " + CLASS_NAME + ".create | with-trace", first);

        ZoneTraceContext.clear();
        ZoneLog.info(CLASS_NAME, "create", "without-trace");
        String second = appender.list.get(1).getFormattedMessage();
        assertEquals("app | unknown | " + CLASS_NAME + ".create | without-trace",
                second, "清理后不应携带 traceId 且无空占位: " + second);
    }

    @Test
    void includeSwitchesShouldHideSegments() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, false, false, "dev", ZoneLogFormat.TEXT);
        ZoneTraceContext.bind("t-200", "r-200");

        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}创建成功", "OID-1");

        String message = appender.list.get(0).getFormattedMessage();
        assertEquals("dev | r-200 | " + CLASS_NAME + ".createOrder | 订单OID-1创建成功",
                message, "关闭开关的段应被裁剪；reqId 无开关、有值即输出；环境段始终保留: " + message);
    }

    @Test
    void genericLogOverloadShouldRespectExplicitLevel() {
        ZoneLog.log(ZoneLogLevel.WARN, CLASS_NAME, "create", "库存不足,订单{}", "OID-2");

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("库存不足,订单OID-2"));
    }

    // ==================== JSON 行模式 ====================

    @Test
    void jsonLineShouldBeParseableWithAllFields() {
        ZoneLogSettings.apply("order-app", ZoneLogLevel.INFO, true, true, "prod", ZoneLogFormat.JSON);
        ZoneTraceContext.bind("t-j1", "r-j1");

        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}创建成功", "OID-1");

        String line = appender.list.get(0).getFormattedMessage();
        JSONObject json = JSON.parseObject(line);
        assertNotNull(json, "JSON 模式输出应为合法单行 JSON: " + line);
        assertTrue(json.getLongValue("time") > 0, "time 应为自产毫秒时间戳: " + line);
        assertEquals("INFO", json.getString("level"));
        assertEquals("order-app", json.getString("appName"));
        assertEquals("prod", json.getString("env"));
        assertEquals("t-j1", json.getString("traceId"));
        assertEquals("r-j1", json.getString("reqId"));
        assertEquals(CLASS_NAME, json.getString("class"));
        assertEquals("createOrder", json.getString("method"));
        assertEquals("订单OID-1创建成功", json.getString("message"));
        assertNull(json.getString("exception"), "未携带异常时 exception 应为 null 占位");
    }

    @Test
    void jsonShouldUseNullPlaceholderForMissingIds() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, true, true, "unknown", ZoneLogFormat.JSON);

        ZoneLog.info(CLASS_NAME, "create", "no-ids");

        JSONObject json = JSON.parseObject(appender.list.get(0).getFormattedMessage());
        assertTrue(json.containsKey("traceId") && json.getString("traceId") == null,
                "未设置 traceId 应为 null 占位而非缺席: " + json);
        assertTrue(json.containsKey("reqId") && json.getString("reqId") == null,
                "未设置 reqId 应为 null 占位而非缺席: " + json);
    }

    @Test
    void jsonShouldCarryExceptionStack() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, true, true, "unknown", ZoneLogFormat.JSON);

        ZoneLog.error(CLASS_NAME, "save", "订单{}保存失败", "OID-9", new IllegalStateException("boom3"));

        ILoggingEvent event = appender.list.get(0);
        JSONObject json = JSON.parseObject(event.getFormattedMessage());
        String exception = json.getString("exception");
        assertNotNull(exception, "携带异常时 exception 应非空");
        assertTrue(exception.contains("IllegalStateException") && exception.contains("boom3"),
                "exception 应含异常类名与消息: " + exception);
        assertTrue(exception.contains("at "), "exception 应为完整堆栈字符串: " + exception);
        assertNull(event.getThrowableProxy(), "JSON 模式堆栈内嵌于 exception 字段，不再重复传递");
    }

    @Test
    void jsonThresholdShouldStillApply() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, true, true, "unknown", ZoneLogFormat.JSON);

        ZoneLog.debug(CLASS_NAME, "create", "should-not-appear");
        assertEquals(0, appender.list.size(), "JSON 模式下级别门槛仍然生效");
    }
}
