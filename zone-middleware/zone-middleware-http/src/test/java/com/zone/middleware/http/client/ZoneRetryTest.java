package com.zone.middleware.http.client;

import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.http.config.ZoneHttpProperties;
import com.zone.middleware.http.config.ZoneOfficialHttpClientProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 失败重试行为测试（熔断能力已移除，由 Sentinel 等专业熔断框架承接）。
 */
class ZoneRetryTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ZoneHttpProperties retryProps(int maxAttempts, long initialBackoffMs) {
        ZoneHttpProperties properties = new ZoneHttpProperties();
        properties.getRetry().setEnabled(true);
        properties.getRetry().setMaxAttempts(maxAttempts);
        properties.getRetry().setInitialBackoff(Duration.ofMillis(initialBackoffMs));
        properties.getRetry().setMaxBackoff(Duration.ofMillis(initialBackoffMs));
        return properties;
    }

    private ZoneHttpTemplate template(ZoneHttpProperties properties) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build();
        return new ZoneHttpTemplate(client, properties,
                new ZoneOfficialHttpClientProperties.Client(), null);
    }

    @Test
    void retryableStatusShouldTriggerRetryUntilSuccess() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/flaky", exchange -> {
            int hit = hits.incrementAndGet();
            int status = hit <= 2 ? 503 : 200;
            byte[] out = (status == 200 ? "{\"name\":\"ok\"}" : "{}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        ZoneHttpTemplateTest.User user = template(retryProps(3, 20))
                .get(baseUrl + "/flaky", ZoneHttpTemplateTest.User.class);
        assertEquals("ok", user.getName());
        assertEquals(3, hits.get(), "前两次 503 + 第三次成功，共 3 次请求");
    }

    @Test
    void exhaustedRetryShouldThrowHttpError() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/always-503", exchange -> {
            hits.incrementAndGet();
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        ZoneBusinessException exception = assertThrows(ZoneBusinessException.class,
                () -> template(retryProps(2, 10)).get(baseUrl + "/always-503", String.class));
        assertEquals(SystemError.HTTP_ERROR, exception.getError());
        assertTrue(exception.getMessage().contains("503"));
        assertEquals(2, hits.get(), "最大尝试 2 次后放弃");
    }

    @Test
    void retryDisabledByDefaultShouldFailOnFirstAttempt() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/single-503", exchange -> {
            hits.incrementAndGet();
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        assertThrows(ZoneBusinessException.class,
                () -> template(new ZoneHttpProperties()).get(baseUrl + "/single-503", String.class));
        assertEquals(1, hits.get(), "默认不重试，仅 1 次请求");
    }

    @Test
    void nonRetryableStatusShouldNotRetry() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/not-found", exchange -> {
            hits.incrementAndGet();
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        assertThrows(ZoneBusinessException.class,
                () -> template(retryProps(3, 10)).get(baseUrl + "/not-found", String.class));
        assertEquals(1, hits.get(), "404 不在可重试状态码内，仅 1 次请求");
    }
}
