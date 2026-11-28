package com.zone.middleware.http.config;

import com.zone.middleware.http.client.ZoneHttpTemplate;
import com.zone.middleware.http.interceptor.ZoneAuthInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.util.List;

/**
 * HTTP 支撑自动装配：构建 JDK HttpClient 与 ZoneHttpTemplate，
 * 收集全部 {@link ZoneAuthInterceptor} Bean 应用于模板。
 * 连接超时与重定向策略取自官方键 spring.http.client.*（未配置兜底 5s / follow-when-possible），
 * 读超时经模板逐请求施加（JDK HttpClient 无 client 级读超时）。
 */
@AutoConfiguration
@ConditionalOnClass(HttpClient.class)
@EnableConfigurationProperties({ZoneHttpProperties.class, ZoneOfficialHttpClientProperties.Client.class})
public class ZoneHttpAutoConfiguration {

    /**
     * 构建 JDK HttpClient。
     *
     * @param client 官方 spring.http.client.* 绑定
     * @return 应用连接超时与重定向策略的 HttpClient
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(HttpClient.class)
    public HttpClient zoneHttpClient(ZoneOfficialHttpClientProperties.Client client) {
        return HttpClient.newBuilder()
                .connectTimeout(client.getConnectTimeout())
                .followRedirects(mapRedirect(client.getRedirects()))
                .build();
    }

    /**
     * 构建统一 HTTP 模板（收集全部鉴权拦截器 Bean）。
     *
     * @param zoneHttpClient JDK HttpClient
     * @param properties     zone.middleware.http.* 行为配置（并发上限、重试）
     * @param client         官方 spring.http.client.* 绑定（读超时逐请求施加）
     * @param interceptors   鉴权拦截器 Provider（按序收集）
     * @return ZoneHttpTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(ZoneHttpTemplate.class)
    public ZoneHttpTemplate zoneHttpTemplate(HttpClient zoneHttpClient,
                                             ZoneHttpProperties properties,
                                             ZoneOfficialHttpClientProperties.Client client,
                                             ObjectProvider<ZoneAuthInterceptor> interceptors) {
        List<ZoneAuthInterceptor> collected = interceptors.orderedStream().toList();
        return new ZoneHttpTemplate(zoneHttpClient, properties, client, collected);
    }

    /**
     * 映射官方重定向策略枚举到 JDK HttpClient 取值。
     *
     * @param redirects 官方重定向策略
     * @return JDK HttpClient 重定向策略
     */
    private HttpClient.Redirect mapRedirect(ZoneOfficialHttpClientProperties.Redirects redirects) {
        return switch (redirects) {
            case NEVER -> HttpClient.Redirect.NEVER;
            case FOLLOW_WHEN_POSSIBLE -> HttpClient.Redirect.NORMAL;
        };
    }
}
