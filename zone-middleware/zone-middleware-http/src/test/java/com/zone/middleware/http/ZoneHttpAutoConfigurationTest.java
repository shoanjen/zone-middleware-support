package com.zone.middleware.http;

import com.zone.middleware.http.config.ZoneHttpAutoConfiguration;
import com.zone.middleware.http.config.ZoneHttpProperties;
import com.zone.middleware.http.interceptor.ZoneAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ZoneHttpAutoConfigurationTest {

    @Configuration
    static class AuthConfig {
        @Bean
        public ZoneAuthInterceptor tokenInterceptor() {
            return builder -> builder.header("Authorization", "Bearer zone");
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ZoneHttpAutoConfiguration.class));

    @Test
    void shouldCreateHttpClientAndTemplateWithDefaults() {
        runner.run(context -> {
            assertNotNull(context.getBean(HttpClient.class));
            ZoneHttpTemplate template = context.getBean(ZoneHttpTemplate.class);
            assertNotNull(template);
        });
    }

    @Test
    void shouldCollectAuthInterceptorBeans() {
        runner.withUserConfiguration(AuthConfig.class).run(context -> {
            assertNotNull(context.getBean(ZoneHttpTemplate.class));
            // 装配无异常且鉴权 Bean 存在即可（拦截逻辑已在模板测试覆盖）
            assertNotNull(context.getBean(ZoneAuthInterceptor.class));
        });
    }

    @Test
    void customPropertiesShouldBind() {
        runner.withPropertyValues(
                        "zone.middleware.http.connect-timeout=2s",
                        "zone.middleware.http.read-timeout=20s",
                        "zone.middleware.http.max-concurrent-requests=8",
                        "zone.middleware.http.retry.enabled=true",
                        "zone.middleware.http.retry.max-attempts=4",
                        "zone.middleware.http.circuit-breaker.enabled=true",
                        "zone.middleware.http.circuit-breaker.failure-threshold=3")
                .run(context -> {
                    ZoneHttpProperties properties = context.getBean(ZoneHttpProperties.class);
                    assertEquals(Duration.ofSeconds(2), properties.getConnectTimeout());
                    assertEquals(Duration.ofSeconds(20), properties.getReadTimeout());
                    assertEquals(8, properties.getMaxConcurrentRequests());
                    assertEquals(true, properties.getRetry().isEnabled());
                    assertEquals(4, properties.getRetry().getMaxAttempts());
                    assertEquals(true, properties.getCircuitBreaker().isEnabled());
                    assertEquals(3, properties.getCircuitBreaker().getFailureThreshold());
                });
    }
}
