package com.zone.middleware.http;

import com.sun.net.httpserver.HttpServer;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.common.result.ZoneResult;
import com.zone.middleware.http.config.ZoneHttpProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneHttpTemplateTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private String name;
        private int age;
    }

    private HttpServer server;
    private String baseUrl;
    private final ExecutorService serverExecutor = Executors.newCachedThreadPool();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    private void respond(int status, String body) {
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    private ZoneHttpTemplate template(ZoneHttpProperties properties) {
        HttpClientBuilder builder = new HttpClientBuilder();
        return new ZoneHttpTemplate(builder.build(), properties, null);
    }

    /** 直接构建 JDK HttpClient（绕开自动配置，测试专用） */
    static class HttpClientBuilder {
        java.net.http.HttpClient build() {
            return java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
        }
    }

    @Test
    void getShouldDeserializeToTargetType() {
        respond(200, "{\"name\":\"zone\",\"age\":3}");
        User user = template(new ZoneHttpProperties()).get(baseUrl + "/user", User.class);
        assertEquals("zone", user.getName());
        assertEquals(3, user.getAge());
    }

    @Test
    void getShouldSupportZoneResultType() {
        respond(200, "{\"success\":true,\"code\":\"SUCCESS\",\"message\":\"成功\","
                + "\"data\":{\"name\":\"zone\",\"age\":3},\"timestamp\":1}");
        ZoneResult<User> result = template(new ZoneHttpProperties())
                .get(baseUrl + "/user", ZoneResult.class);
        assertTrue(result.isSuccess());
        assertEquals("SUCCESS", result.getCode());
    }

    @Test
    void postShouldSendJsonAndDeserializeEcho() {
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String bodyString = new String(body, StandardCharsets.UTF_8);
            String out = com.alibaba.fastjson2.JSON.toJSONString(java.util.Map.of(
                    "echoContentType", contentType, "body", bodyString));
            byte[] outBytes = out.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, outBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(outBytes);
            }
        });
        server.start();

        Map<String, String> echo = template(new ZoneHttpProperties())
                .post(baseUrl + "/echo", new User("zone", 3), Map.class);
        assertEquals("application/json", echo.get("echoContentType"));
        assertTrue(((String) echo.get("body")).contains("\"name\":\"zone\""));
    }

    @Test
    void authInterceptorShouldApplyToEveryRequest() {
        server.createContext("/auth", exchange -> {
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] out = ("{\"token\":\"" + token + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        java.net.http.HttpClient client = new HttpClientBuilder().build();
        ZoneHttpTemplate httpTemplate = new ZoneHttpTemplate(
                client, new ZoneHttpProperties(),
                java.util.List.of(builder -> builder.header("Authorization", "Bearer zone-token")));

        Map<?, ?> first = httpTemplate.get(baseUrl + "/auth", Map.class);
        Map<?, ?> second = httpTemplate.post(baseUrl + "/auth", new User("x", 1), Map.class);
        assertEquals("Bearer zone-token", first.get("token"));
        assertEquals("Bearer zone-token", second.get("token"));
    }

    @Test
    void non2xxShouldThrowWithSystemHttpError() {
        respond(404, "{\"message\":\"not found\"}");
        ZoneBusinessException exception = assertThrows(ZoneBusinessException.class,
                () -> template(new ZoneHttpProperties()).get(baseUrl + "/missing", User.class));
        assertEquals(SystemError.HTTP_ERROR, exception.getError());
        assertTrue(exception.getMessage().contains("404"));
    }

    @Test
    void readTimeoutShouldFailFast() {
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        ZoneHttpProperties properties = new ZoneHttpProperties();
        properties.setReadTimeout(Duration.ofMillis(150));
        ZoneBusinessException exception = assertThrows(ZoneBusinessException.class,
                () -> template(properties).get(baseUrl + "/slow", User.class));
        assertEquals(SystemError.HTTP_ERROR, exception.getError());
    }

    @Test
    void concurrencyLimitShouldBoundInFlightRequests() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        server.createContext("/burst", exchange -> {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            } finally {
                inFlight.decrementAndGet();
            }
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        ZoneHttpProperties properties = new ZoneHttpProperties();
        properties.setMaxConcurrentRequests(2);
        ZoneHttpTemplate httpTemplate = template(properties);

        int requests = 6;
        CountDownLatch latch = new CountDownLatch(requests);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                pool.execute(() -> {
                    try {
                        httpTemplate.get(baseUrl + "/burst", User.class);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(15, TimeUnit.SECONDS));
        }
        assertEquals(null, failure.get(), "全部请求应成功");
        assertTrue(maxInFlight.get() <= 2,
                "任意时刻在途请求不应超过上限，实测: " + maxInFlight.get());
    }
}
