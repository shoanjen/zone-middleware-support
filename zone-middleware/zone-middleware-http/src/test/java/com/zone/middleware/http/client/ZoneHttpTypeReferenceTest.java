package com.zone.middleware.http.client;

import com.alibaba.fastjson2.TypeReference;
import com.zone.middleware.common.result.ZoneResult;
import com.zone.middleware.http.config.ZoneHttpProperties;
import com.zone.middleware.http.config.ZoneOfficialHttpClientProperties;
import com.sun.net.httpserver.HttpServer;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TypeReference 泛型反序列化重载测试：ZoneResult 嵌套泛型 data 不退化为 JSONObject。
 */
class ZoneHttpTypeReferenceTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private String orderNo;
        private int amount;
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

    private ZoneHttpTemplate template() {
        return new ZoneHttpTemplate(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build(), new ZoneHttpProperties(),
                new ZoneOfficialHttpClientProperties.Client(), null);
    }

    private static final String SINGLE_ORDER_BODY = "{\"success\":true,\"code\":\"200\","
            + "\"message\":\"成功\",\"data\":{\"orderNo\":\"NO-1\",\"amount\":99},\"timestamp\":1}";
    private static final String ORDER_LIST_BODY = "{\"success\":true,\"code\":\"200\","
            + "\"message\":\"成功\",\"data\":[{\"orderNo\":\"NO-1\",\"amount\":99},"
            + "{\"orderNo\":\"NO-2\",\"amount\":120}],\"timestamp\":1}";

    @Test
    void getWithGenericTypeReferenceShouldRestoreData() {
        respond(200, SINGLE_ORDER_BODY);
        ZoneResult<Order> result = template()
                .get(baseUrl + "/order", new TypeReference<ZoneResult<Order>>() {});
        assertTrue(result.isSuccess());
        assertEquals(Order.class, result.getData().getClass(), "data 应还原为 Order 而非 JSONObject");
        assertEquals("NO-1", result.getData().getOrderNo());
        assertEquals(99, result.getData().getAmount());
    }

    @Test
    void getWithListGenericTypeReferenceShouldRestoreElements() {
        respond(200, ORDER_LIST_BODY);
        ZoneResult<List<Order>> result = template()
                .get(baseUrl + "/orders", new TypeReference<ZoneResult<List<Order>>>() {});
        assertEquals(2, result.getData().size());
        assertEquals(Order.class, result.getData().get(0).getClass(), "列表元素应为 Order");
        assertEquals(120, result.getData().get(1).getAmount());
    }

    @Test
    void postWithGenericTypeReferenceShouldRestoreData() {
        respond(200, SINGLE_ORDER_BODY);
        ZoneResult<Order> result = template()
                .post(baseUrl + "/order", Map.of("orderNo", "NO-1"),
                        new TypeReference<ZoneResult<Order>>() {});
        assertEquals(Order.class, result.getData().getClass());
        assertEquals(99, result.getData().getAmount());
    }

    @Test
    void overloadsShouldForwardCustomHeaders() {
        server.createContext("/echo", exchange -> {
            String marker = exchange.getRequestHeaders().getFirst("X-Zone-Marker");
            String out = "{\"success\":true,\"code\":\"200\",\"message\":\""
                    + (marker == null ? "none" : marker) + "\",\"data\":{\"orderNo\":\"NO-1\",\"amount\":1},"
                    + "\"timestamp\":1}";
            byte[] outBytes = out.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, outBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(outBytes);
            }
        });
        server.start();
        ZoneHttpTemplate t = template();

        ZoneResult<Order> viaGet = t.get(baseUrl + "/echo", Map.of("X-Zone-Marker", "get-header"),
                new TypeReference<ZoneResult<Order>>() {});
        assertEquals("get-header", viaGet.getData().getOrderNo().isEmpty() ? viaGet.getMessage() : viaGet.getMessage());
        assertEquals("get-header", viaGet.getMessage(), "get 带头变体应透传自定义头");

        ZoneResult<Order> viaPost = t.post(baseUrl + "/echo", Map.of("k", "v"),
                Map.of("X-Zone-Marker", "post-header"), new TypeReference<ZoneResult<Order>>() {});
        assertEquals("post-header", viaPost.getMessage(), "post 带头变体应透传自定义头");
    }
}
