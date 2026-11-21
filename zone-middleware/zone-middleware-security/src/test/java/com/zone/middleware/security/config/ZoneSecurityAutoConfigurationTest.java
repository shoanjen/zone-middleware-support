package com.zone.middleware.security.config;

import com.zone.middleware.security.token.ZoneTokenService;
import com.zone.middleware.security.web.ZoneRepeatableReadFilter;
import com.zone.middleware.security.web.ZoneSignCheckInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全装配单测：Bean 条件装配与 secret 启动期快速失败。
 */
class ZoneSecurityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ZoneSecurityAutoConfiguration.class))
            .withUserConfiguration(RedisStubConfig.class);

    @Configuration(proxyBeanMethods = false)
    static class RedisStubConfig {
        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return org.mockito.Mockito.mock(StringRedisTemplate.class);
        }
    }

    @Test
    void shouldAssembleSecurityBeansWhenSecretConfigured() {
        runner.withPropertyValues("zone.middleware.security.sign.secret=runner-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(ZoneTokenService.class);
                    assertThat(context).hasSingleBean(ZoneSignCheckInterceptor.class);
                    // 过滤器经 FilterRegistrationBean 注册，实例类型断言
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    FilterRegistrationBean<?> registration =
                            context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getFilter()).isInstanceOf(ZoneRepeatableReadFilter.class);
                });
    }

    @Test
    void shouldSkipInterceptorWhenSignDisabled() {
        runner.withPropertyValues(
                        "zone.middleware.security.sign.enabled=false",
                        "zone.middleware.security.sign.secret=runner-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(ZoneTokenService.class);
                    assertThat(context).doesNotHaveBean(ZoneSignCheckInterceptor.class);
                    // 过滤器注册随拦截器一并缺席（无拦截即无需包装）
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    @Test
    void shouldFailFastWhenSignEnabledWithoutSecret() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void shouldSkipAllWhenRedisTemplateAbsent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ZoneSecurityAutoConfiguration.class))
                .withPropertyValues("zone.middleware.security.sign.secret=runner-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ZoneTokenService.class);
                    assertThat(context).doesNotHaveBean(ZoneSignCheckInterceptor.class);
                });
    }
}
