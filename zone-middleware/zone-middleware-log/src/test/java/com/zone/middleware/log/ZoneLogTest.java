package com.zone.middleware.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        ZoneLogSettings.apply("app", ZoneLogLevel.DEBUG, true, true);
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
        ZoneLogSettings.apply("order-service", ZoneLogLevel.INFO, true, true);
        ZoneTraceContext.setTraceId("trace-001");

        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}创建成功", "OID-1");

        String message = appender.list.get(0).getFormattedMessage();
        int appIdx = message.indexOf("order-service");
        int traceIdx = message.indexOf("trace-001");
        int methodIdx = message.indexOf(CLASS_NAME + ".createOrder");
        int msgIdx = message.indexOf("订单OID-1创建成功");
        assertTrue(appIdx >= 0 && traceIdx > appIdx && methodIdx > traceIdx && msgIdx > methodIdx,
                "字段顺序应为 应用名|traceId|类名.方法名|消息: " + message);
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
        ZoneLogSettings.apply("app", ZoneLogLevel.DEBUG, true, true);
        ZoneLog.debug(CLASS_NAME, "create", "debug-now-visible");
        assertEquals(1, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("debug-now-visible"));
    }

    @Test
    void traceIdShouldBeCarriedAndCleared() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, true, true);

        ZoneTraceContext.setTraceId("t-100");
        ZoneLog.info(CLASS_NAME, "create", "with-trace");
        assertTrue(appender.list.get(0).getFormattedMessage().contains("t-100"));

        ZoneTraceContext.clear();
        ZoneLog.info(CLASS_NAME, "create", "without-trace");
        String second = appender.list.get(1).getFormattedMessage();
        assertTrue(!second.contains("t-100"), "清理后不应携带 traceId: " + second);
    }

    @Test
    void includeSwitchesShouldHideSegments() {
        ZoneLogSettings.apply("app", ZoneLogLevel.INFO, false, false);
        ZoneTraceContext.setTraceId("t-200");

        ZoneLog.info(CLASS_NAME, "createOrder", "订单{}创建成功", "OID-1");

        String message = appender.list.get(0).getFormattedMessage();
        assertEquals(CLASS_NAME + ".createOrder | 订单OID-1创建成功", message);
    }

    @Test
    void genericLogOverloadShouldRespectExplicitLevel() {
        ZoneLog.log(ZoneLogLevel.WARN, CLASS_NAME, "create", "库存不足,订单{}", "OID-2");

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("库存不足,订单OID-2"));
    }
}
