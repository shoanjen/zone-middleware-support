package com.zone.middleware.http.config;

import com.zone.middleware.http.ZoneHttpTemplate;
import com.zone.middleware.http.interceptor.ZoneAuthInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.List;

/**
 * HTTP 支撑自动装配：构建 JDK HttpClient 与 ZoneHttpTemplate，
 * 收集全部 {@link ZoneAuthInterceptor} Bean 应用于模板。
 */
@Configuration
@ConditionalOnClass(HttpClient.class)
@EnableConfigurationProperties(ZoneHttpProperties.class)
public class ZoneHttpAutoConfiguration {

    /**
     * 构建 JDK HttpClient。
     *
     * @param properties zone.middleware.http.* 配置
     * @return 应用连接超时与重定向策略的 HttpClient
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(HttpClient.class)
    public HttpClient zoneHttpClient(ZoneHttpProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(mapRedirect(properties.getRedirect()))
                .build();
    }

    /**
     * 构建统一 HTTP 模板（收集全部鉴权拦截器 Bean）。
     *
     * @param zoneHttpClient JDK HttpClient
     * @param properties     zone.middleware.http.* 配置
     * @param interceptors   鉴权拦截器 Provider（按序收集）
     * @return ZoneHttpTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(ZoneHttpTemplate.class)
    public ZoneHttpTemplate zoneHttpTemplate(HttpClient zoneHttpClient,
                                             ZoneHttpProperties properties,
                                             ObjectProvider<ZoneAuthInterceptor> interceptors) {
        List<ZoneAuthInterceptor> collected = interceptors.orderedStream().toList();
        return new ZoneHttpTemplate(zoneHttpClient, properties, collected);
    }

    /**
     * 映射重定向策略枚举到 JDK HttpClient 取值。
     *
     * @param redirect zone 重定向策略
     * @return JDK HttpClient 重定向策略
     */
    private HttpClient.Redirect mapRedirect(ZoneHttpProperties.Redirect redirect) {
        return switch (redirect) {
            case NONE -> HttpClient.Redirect.NEVER;
            case ALWAYS -> HttpClient.Redirect.ALWAYS;
            default -> HttpClient.Redirect.NORMAL;
        };
    }
}
