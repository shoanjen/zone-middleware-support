package com.zone.middleware.security.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zone.middleware.common.constant.ZoneConstants;
import com.zone.middleware.common.result.ZoneResult;
import com.zone.middleware.security.annotation.ZoneSignCheck;
import com.zone.middleware.security.context.ZoneSecurityContext;
import com.zone.middleware.security.sign.ZoneSignUtils;
import com.zone.middleware.security.token.ZoneTokenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端集成测试：自动装配全链路（imports 注册 → 过滤器 → 拦截器 → nonce/Redis）。
 * 依赖共享 Redis（localhost:6379），不可达时整类跳过。
 */
@SpringBootTest(classes = {ZoneSecurityE2ETest.E2EApp.class, ZoneSecurityE2ETest.E2EOrderController.class},
        properties = {
                "zone.middleware.security.sign.secret=e2e-secret",
                "zone.middleware.security.sign.timestamp-window=5m",
                "zone.middleware.redis.database=13"
        })
@AutoConfigureMockMvc
class ZoneSecurityE2ETest {

    private static final String SECRET = "e2e-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ZoneTokenService tokenService;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(isRedisReachable(), "共享 Redis (localhost:6379) 不可达，跳过端到端测试");
    }

    /** 端到端样例应用（经 imports 文件触发自动装配） */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class E2EApp {
    }

    /** 标注 @ZoneSignCheck 的样例端点 */
    @RestController
    static class E2EOrderController {

        @ZoneSignCheck
        @PostMapping("/security-e2e/order")
        public ZoneResult<String> createOrder(@RequestBody String body) {
            return ZoneResult.ok(JSON.parseObject(body).getString("orderNo"));
        }

        @ZoneSignCheck
        @GetMapping("/security-e2e/query")
        public ZoneResult<String> query() {
            return ZoneResult.ok("query-ok");
        }

        /** requireLogin 端点：业务经 ZoneSecurityContext 取登录 userId，验证上下文全链路传递 */
        @ZoneSignCheck(requireLogin = true)
        @GetMapping("/security-e2e/profile")
        public ZoneResult<String> profile() {
            return ZoneResult.ok(ZoneSecurityContext.currentUserId());
        }

        /** 未标注端点：不受拦截 */
        @GetMapping("/security-e2e/open")
        public ZoneResult<String> open() {
            return ZoneResult.ok("open-ok");
        }
    }

    @Test
    void signedPostShouldReachBusiness() throws Exception {
        MvcResult result = mockMvc.perform(signedPost(freshReqId(), "A1001"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("\"success\":true"));
    }

    @Test
    void replayedPostShouldBeRejected() throws Exception {
        String replayedReqId = freshReqId();
        mockMvc.perform(signedPost(replayedReqId, "A2001"))
                .andExpect(status().isOk());
        // 同 reqId 原样重放：nonce 拒绝
        mockMvc.perform(signedPost(replayedReqId, "A2001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedPostShouldBeRejected() throws Exception {
        // 按 orderNo=A2002 签名，实际发送 A2TAMPER（单一参数源，保证差异仅在篡改处）
        Map<String, Object> body = e2eBody(freshReqId(), "A2002");
        String json = new JSONObject(new LinkedHashMap<>(body)).toJSONString();
        String tamperedJson = json.replace("A2002", "A2TAMPER");

        mockMvc.perform(post("/security-e2e/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedJson)
                        .header(ZoneConstants.HEADER_ACCESS_TOKEN, signOf(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacySignFieldShouldNotBeConsumed() throws Exception {
        // body 携带存量 sign 字段但缺失签名请求头：仍拒绝（请求头是唯一来源）
        String json = jsonWithLegacySign(freshReqId(), "A3001", "legacy-sign-value");
        mockMvc.perform(post("/security-e2e/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedGetShouldReachBusiness() throws Exception {
        String queryReqId = freshReqId();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reqId", queryReqId);
        params.put("timestamp", System.currentTimeMillis());
        params.put("platform", "web");

        mockMvc.perform(get("/security-e2e/query")
                        .param("reqId", queryReqId)
                        .param("timestamp", String.valueOf(params.get("timestamp")))
                        .param("platform", "web")
                        .header(ZoneConstants.HEADER_ACCESS_TOKEN, ZoneSignUtils.sign(params, SECRET)))
                .andExpect(status().isOk());
    }

    @Test
    void unannotatedEndpointShouldBypassCheck() throws Exception {
        mockMvc.perform(get("/security-e2e/open"))
                .andExpect(status().isOk());
    }

    // ==================== 强制登录（requireLogin） ====================

    @Test
    void loginRequiredShouldExposeUserIdToBusiness() throws Exception {
        String token = tokenService.issueToken("u-e2e-1001",
                Map.of("userId", "u-e2e-1001", "name", "张三"));
        try {
            MvcResult result = mockMvc.perform(signedGet("/security-e2e/profile", freshReqId())
                            .header(ZoneConstants.HEADER_AUTH_TOKEN, token))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("\"success\":true"));
            // 业务代码经 ZoneSecurityContext 取到的 userId 回传于响应 data
            assertTrue(body.contains("u-e2e-1001"), "响应应包含登录 userId: " + body);
        } finally {
            tokenService.revokeToken(token);
        }
    }

    @Test
    void loginRequiredShouldRejectMissingToken() throws Exception {
        mockMvc.perform(signedGet("/security-e2e/profile", freshReqId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiredShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(signedGet("/security-e2e/profile", freshReqId())
                        .header(ZoneConstants.HEADER_AUTH_TOKEN, "not-exists-token"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 辅助 ====================

    /**
     * 发送签名正确的 GET（query 顶层参数参与签名）。
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    signedGet(String uri, String reqId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reqId", reqId);
        params.put("timestamp", System.currentTimeMillis());
        params.put("platform", "web");
        return get(uri)
                .param("reqId", reqId)
                .param("timestamp", String.valueOf(params.get("timestamp")))
                .param("platform", "web")
                .header(ZoneConstants.HEADER_ACCESS_TOKEN, signOf(params));
    }

    /**
     * 发送签名正确的 POST（body 顶层字段参与签名；单一参数源保证签名与报文一致）。
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    signedPost(String reqId, String orderNo) {
        Map<String, Object> body = e2eBody(reqId, orderNo);
        String json = new JSONObject(new LinkedHashMap<>(body)).toJSONString();
        return post("/security-e2e/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .header(ZoneConstants.HEADER_ACCESS_TOKEN, signOf(body));
    }

    /**
     * 构建顶层参数（与请求体一致的 Java 类型，模拟 fastjson2 反序列化产物）。
     */
    private Map<String, Object> e2eBody(String reqId, String orderNo) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reqId", reqId);
        params.put("timestamp", System.currentTimeMillis());
        params.put("platform", "web");
        params.put("orderNo", orderNo);
        return params;
    }

    /**
     * 构建携带存量 sign 字段的请求体 JSON。
     */
    private String jsonWithLegacySign(String reqId, String orderNo, String legacySign) {
        Map<String, Object> body = e2eBody(reqId, orderNo);
        JSONObject json = new JSONObject(new LinkedHashMap<>(body));
        json.put("sign", legacySign);
        return json.toJSONString();
    }

    /**
     * 计算参数签名。
     */
    private String signOf(Map<String, Object> params) {
        return ZoneSignUtils.sign(params, SECRET);
    }

    /**
     * 生成本次运行唯一的 reqId：nonce TTL（时间窗）内跨运行复用固定 reqId 会被判重放。
     */
    private static String freshReqId() {
        return ZoneSignUtils.generateReqId();
    }

    /**
     * 共享 Redis 实例可达性探测。
     */
    private static boolean isRedisReachable() {
        try (Socket socket = new Socket("127.0.0.1", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }
}
