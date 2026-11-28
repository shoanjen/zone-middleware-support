package com.zone.middleware.http.client;

import com.alibaba.fastjson2.TypeReference;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.common.util.JsonUtil;
import com.zone.middleware.http.config.ZoneHttpProperties;
import com.zone.middleware.http.config.ZoneOfficialHttpClientProperties;
import com.zone.middleware.http.interceptor.ZoneAuthInterceptor;
import com.zone.middleware.log.ZoneLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Zone 统一 HTTP 模板：基于 JDK 21 内置 HttpClient。
 *
 * <p>能力：默认/自定义客户端配置、通用 GET/POST（fastjson2 序列化，经 {@link JsonUtil} 收口）、
 * 自定义鉴权、连接复用与并发上限（信号量）、可配置重试。熔断保护已移除——需要熔断的上游
 * 应用请接入 Sentinel / resilience4j 等专业熔断框架，以资源规则包裹模板调用。</p>
 */
public class ZoneHttpTemplate {

    private final HttpClient httpClient;
    private final ZoneHttpProperties properties;
    private final Duration readTimeout;
    private final Semaphore concurrencyLimiter;
    private final List<ZoneAuthInterceptor> authInterceptors;

    /**
     * 构建统一 HTTP 模板。
     *
     * @param httpClient       底层 JDK HttpClient（连接复用、连接超时与重定向策略由此承载）
     * @param properties       zone.middleware.http.* 行为配置（并发上限、重试）
     * @param client           官方 spring.http.client.* 绑定（读超时逐请求施加）
     * @param authInterceptors 鉴权拦截器列表（可为 null/空，均会被逐个应用于每个请求）
     */
    public ZoneHttpTemplate(HttpClient httpClient, ZoneHttpProperties properties,
                            ZoneOfficialHttpClientProperties.Client client,
                            List<ZoneAuthInterceptor> authInterceptors) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.readTimeout = client.getReadTimeout();
        this.authInterceptors = authInterceptors == null ? List.of() : List.copyOf(authInterceptors);
        this.concurrencyLimiter = new Semaphore(Math.max(1, properties.getMaxConcurrentRequests()));
    }

    /**
     * 通用 GET 请求。
     *
     * @param url          目标地址
     * @param responseType 响应目标类型（String 原样返回，其他类型 fastjson2 反序列化）
     * @param <T>          响应类型
     * @return 反序列化后的响应体
     * @throws ZoneBusinessException 非 2xx（重试耗尽或不可重试）或 IO 异常
     */
    public <T> T get(String url, Class<T> responseType) {
        return get(url, null, responseType);
    }

    /**
     * 通用 GET 请求（携带请求头）。
     *
     * @param url          目标地址
     * @param headers      附加请求头，可为 null
     * @param responseType 响应目标类型
     * @param <T>          响应类型
     * @return 反序列化后的响应体
     * @throws ZoneBusinessException 非 2xx 或 IO 异常
     */
    public <T> T get(String url, Map<String, String> headers, Class<T> responseType) {
        return exchange(HttpMethod.GET, url, null, headers, responseType);
    }

    /**
     * 通用 POST 请求（JSON 请求体）。
     *
     * @param url          目标地址
     * @param body         请求体对象（fastjson2 序列化为 JSON 发送），可为 null
     * @param responseType 响应目标类型
     * @param <T>          响应类型
     * @return 反序列化后的响应体
     * @throws ZoneBusinessException 非 2xx 或 IO 异常
     */
    public <T> T post(String url, Object body, Class<T> responseType) {
        return post(url, body, null, responseType);
    }

    /**
     * 通用 POST 请求（JSON 请求体 + 请求头）。
     *
     * @param url          目标地址
     * @param body         请求体对象，可为 null
     * @param headers      附加请求头，可为 null
     * @param responseType 响应目标类型
     * @param <T>          响应类型
     * @return 反序列化后的响应体
     * @throws ZoneBusinessException 非 2xx 或 IO 异常
     */
    public <T> T post(String url, Object body, Map<String, String> headers, Class<T> responseType) {
        return exchange(HttpMethod.POST, url, body, headers, responseType);
    }

    /**
     * 通用 GET 请求（泛型反序列化）：以 fastjson2 {@link TypeReference} 声明完整泛型类型，
     * 适用于 {@code ZoneResult<Order>}、{@code ZoneResult<List<Order>>} 等嵌套泛型场景
     * （data 还原为目标类型而非退化为 JSONObject）。
     *
     * @param url          目标地址
     * @param responseType 泛型响应类型声明
     * @param <T>          响应类型
     * @return 按声明泛型反序列化的响应体
     * @throws ZoneBusinessException 非 2xx 或 IO 异常
     */
    public <T> T get(String url, TypeReference<T> responseType) {
        return get(url, null, responseType);
    }

    /**
     * 通用 GET 请求（泛型反序列化 + 请求头）。
     *
     * @param url          目标地址
     * @param headers      附加请求头，可为 null
     * @param responseType 泛型响应类型声明
     * @param <T>          响应类型
     * @return 按声明泛型反序列化的响应体
     * @throws ZoneBusinessException 非 2xx 或 IO 异常
     */
    public <T> T get(String url, Map<String, String> headers, TypeReference<T> responseType) {
        return exchange(HttpMethod.GET, url, null, headers, responseType);
    }

    /**
     * 通用 POST 请求（JSON 请求体 + 泛型反序列化）。
     *
     * @param url          目标地址
     * @param body         请求体对象（fastjson2 序列化为 JSON 发送），可为 null
     * @param responseType 泛型响应类型声明
     * @param <T>          响应类型
     * @return 按声明泛型反序列化的响应体
     * @throws ZoneBusinessException 非 2xx 或 IO 异常
     */
    public <T> T post(String url, Object body, TypeReference<T> responseType) {
        return post(url, body, null, responseType);
    }

    /**
     * 通用 POST 请求（JSON 请求体 + 请求头 + 泛型反序列化）。
     *
     * @param url          目标地址
     * @param body         请求体对象，可为 null
     * @param headers      附加请求头，可为 null
     * @param responseType 泛型响应类型声明
     * @param <T>          响应类型
     * @return 按声明泛型反序列化的响应体
     * @throws ZoneBusinessException 非 2xx 或 IO 异常
     */
    public <T> T post(String url, Object body, Map<String, String> headers, TypeReference<T> responseType) {
        return exchange(HttpMethod.POST, url, body, headers, responseType);
    }

    // ==================== 内部实现 ====================

    private enum HttpMethod {
        GET, POST
    }

    /**
     * 请求统一入口：异常语义统一。
     *
     * @param method      HTTP 方法
     * @param url         目标地址
     * @param body        请求体（仅 POST）
     * @param headers     附加请求头
     * @param responseType 响应目标类型（Class 或 TypeReference）
     * @param <T>         响应类型
     * @return 反序列化后的响应体
     * @throws ZoneBusinessException 底层失败（HTTP_ERROR）
     */
    private <T> T exchange(HttpMethod method, String url, Object body,
                           Map<String, String> headers, Object responseType) {
        return executeWithRetry(method, url, body, headers, responseType);
    }

    /**
     * 带重试的请求执行：可重试状态码/IO 异常按退避策略重试，耗尽后抛 HTTP_ERROR。
     *
     * @param method      HTTP 方法
     * @param url         目标地址
     * @param body        请求体
     * @param headers     附加请求头
     * @param responseType 响应目标类型（Class 或 TypeReference）
     * @param <T>         响应类型
     * @return 反序列化后的响应体
     * @throws ZoneBusinessException 重试耗尽、IO 异常或被中断（HTTP_ERROR）
     */
    private <T> T executeWithRetry(HttpMethod method, String url, Object body,
                                   Map<String, String> headers, Object responseType) {
        ZoneHttpProperties.Retry retry = properties.getRetry();
        int attempts = retry.isEnabled() ? Math.max(1, retry.getMaxAttempts()) : 1;
        int lastStatus = -1;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                sleep(backoff(retry, attempt));
            }
            try {
                HttpResponse<String> response = send(method, url, body, headers);
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return parseBody(response.body(), responseType);
                }
                lastStatus = status;
                boolean retryable = retry.isEnabled()
                        && retry.getRetryableStatuses().contains(status)
                        && attempt < attempts;
                if (!retryable) {
                    break;
                }
            } catch (IOException e) {
                if (retry.isEnabled() && retry.isRetryOnIoError() && attempt < attempts) {
                    continue;
                }
                throw new ZoneBusinessException(SystemError.HTTP_ERROR,
                        "HTTP 请求 IO 异常: " + url + " - " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ZoneBusinessException(SystemError.HTTP_ERROR,
                        "HTTP 请求被中断: " + url, e);
            }
        }
        if (properties.getRetry().isEnabled()) {
            ZoneLog.warn("ZoneHttpTemplate", "executeWithRetry",
                    "重试耗尽，最后状态码 {}，url: {}", lastStatus, url);
        }
        throw new ZoneBusinessException(SystemError.HTTP_ERROR,
                "HTTP " + lastStatus + " 响应来自 " + url);
    }

    /**
     * 构建并发送请求（鉴权拦截、并发上限信号量、UTF-8 响应体）。
     *
     * @param method  HTTP 方法
     * @param url     目标地址
     * @param body    请求体（仅 POST 使用）
     * @param headers 附加请求头
     * @return 原始字符串响应
     * @throws IOException              网络 IO 失败
     * @throws InterruptedException 发送被中断
     */
    private HttpResponse<String> send(HttpMethod method, String url, Object body,
                                      Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        for (ZoneAuthInterceptor interceptor : authInterceptors) {
            interceptor.apply(builder);
        }
        if (method == HttpMethod.POST && body != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtil.toJson(body), StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        boolean acquired = false;
        try {
            concurrencyLimiter.acquire();
            acquired = true;
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } finally {
            if (acquired) {
                concurrencyLimiter.release();
            }
        }
    }

    /**
     * 解析响应体：String 原样返回，其他类型按 Class 或 TypeReference 由 fastjson2 反序列化
     * （TypeReference 支持完整泛型树，data 不退化为 JSONObject；经 {@link JsonUtil} 收口）。
     *
     * @param body        响应体字符串
     * @param responseType 目标类型（Class 或 TypeReference）
     * @param <T>         目标类型
     * @return 解析后的对象（空响应体返回 null）
     */
    @SuppressWarnings("unchecked")
    private <T> T parseBody(String body, Object responseType) {
        if (responseType instanceof Class<?> clazz && clazz == String.class) {
            return (T) body;
        }
        if (body == null || body.isEmpty()) {
            return null;
        }
        if (responseType instanceof Class<?> clazz) {
            return (T) JsonUtil.fromJson(body, clazz);
        }
        if (responseType instanceof TypeReference<?> typeReference) {
            return (T) JsonUtil.fromJson(body, typeReference);
        }
        throw new IllegalArgumentException("不支持的响应类型声明: " + responseType.getClass().getName());
    }

    /**
     * 计算第 attempt 次尝试的指数退避等待（初始信 × 倍数^(n-1)，封顶 maxBackoff）。
     *
     * @param retry  重试配置
     * @param attempt 尝试序号（1 起）
     * @return 等待时长
     */
    private Duration backoff(ZoneHttpProperties.Retry retry, int attempt) {
        long initial = retry.getInitialBackoff().toMillis();
        double multiplier = Math.max(1.0, retry.getBackoffMultiplier());
        long wait = Math.round(initial * Math.pow(multiplier, attempt - 1));
        long capped = Math.min(wait, retry.getMaxBackoff().toMillis());
        return Duration.ofMillis(Math.max(0, capped));
    }

    /**
     * 重试间等待。
     *
     * @param duration 等待时长
     * @throws ZoneBusinessException 等待被中断（HTTP_ERROR，并复位中断标记）
     */
    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ZoneBusinessException(SystemError.HTTP_ERROR, "重试等待被中断", e);
        }
    }
}
