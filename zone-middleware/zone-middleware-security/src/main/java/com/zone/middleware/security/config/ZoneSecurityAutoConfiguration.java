package com.zone.middleware.security.config;

import com.zone.middleware.log.ZoneLog;
import com.zone.middleware.redis.config.ZoneRedisAutoConfiguration;
import com.zone.middleware.security.token.ZoneTokenService;
import com.zone.middleware.security.web.ZoneRepeatableReadFilter;
import com.zone.middleware.security.web.ZoneSignCheckInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 安全支撑自动装配：注册签名校验拦截器（@ZoneSignCheck 端点生效）、body 可重读过滤器
 * 与登录令牌服务。
 *
 * <p>装配条件：</p>
 * <ul>
 *   <li>Redis 底座存在（{@link StringRedisTemplate} Bean 由 zone-redis 模块装配提供），
 *       两者均为 fail-closed 能力，无 Redis 不装配；</li>
 *   <li>签名校验默认启用（{@code zone.middleware.security.sign.enabled=true}），
 *       启用时 {@code sign.secret} 必须配置，否则启动期快速失败；
 *       纯令牌场景可显式关闭；</li>
 *   <li>Web 层组件仅在 Servlet Web 应用中注册。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StringRedisTemplate.class)
@AutoConfigureAfter({ZoneRedisAutoConfiguration.class, RedisAutoConfiguration.class})
@EnableConfigurationProperties(ZoneSecurityProperties.class)
public class ZoneSecurityAutoConfiguration {

    /** 类名（ZoneLog 埋点用） */
    private static final String CLASS_NAME = "ZoneSecurityAutoConfiguration";

    /**
     * 构建登录令牌服务。
     *
     * @param properties     zone.middleware.security.* 配置
     * @param redisTemplate  字符串 Redis 模板
     * @return ZoneTokenService 实例
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public ZoneTokenService zoneTokenService(ZoneSecurityProperties properties,
                                             StringRedisTemplate redisTemplate) {
        return new ZoneTokenService(properties, redisTemplate);
    }

    /**
     * 构建签名校验拦截器（默认启用，可经 {@code zone.middleware.security.sign.enabled=false}
     * 关闭以适配纯令牌场景）；启用时 secret 缺失启动期快速失败。
     *
     * @param properties    zone.middleware.security.* 配置
     * @param redisTemplate 字符串 Redis 模板（nonce 存储）
     * @param tokenService  登录令牌服务（requireLogin=true 端点登录态解析）
     * @return ZoneSignCheckInterceptor 实例
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "zone.middleware.security.sign", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ZoneSignCheckInterceptor zoneSignCheckInterceptor(ZoneSecurityProperties properties,
                                                             StringRedisTemplate redisTemplate,
                                                             ZoneTokenService tokenService) {
        if (!StringUtils.hasText(properties.getSign().getSecret())) {
            throw new IllegalStateException(
                    "zone.middleware.security.sign.secret 未配置：签名校验启用时必须配置签名密钥，"
                            + "或设置 zone.middleware.security.sign.enabled=false 关闭签名校验");
        }
        return new ZoneSignCheckInterceptor(properties, redisTemplate, tokenService);
    }

    /**
     * 注册 body 可重读过滤器（最高优先级，先于一切业务过滤器包装 POST 请求；
     * 签名校验关闭时不装配，纯令牌场景零开销）。
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnMissingBean(ZoneRepeatableReadFilter.class)
    @ConditionalOnProperty(prefix = "zone.middleware.security.sign", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<ZoneRepeatableReadFilter> zoneRepeatableReadFilterRegistration() {
        FilterRegistrationBean<ZoneRepeatableReadFilter> registration =
                new FilterRegistrationBean<>(new ZoneRepeatableReadFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Servlet Web 应用中注册拦截器到全局路径（未标注 @ZoneSignCheck 的端点不受拦截）。
     *
     * @param interceptorProvider 签名校验拦截器 Provider（sign.enabled=false 时缺席）
     * @param properties          zone.middleware.security.* 配置
     * @return WebMvcConfigurer
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public WebMvcConfigurer zoneSecurityWebMvcConfigurer(
            ObjectProvider<ZoneSignCheckInterceptor> interceptorProvider,
            ZoneSecurityProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                ZoneSignCheckInterceptor interceptor = interceptorProvider.getIfAvailable();
                if (interceptor != null) {
                    registry.addInterceptor(interceptor).addPathPatterns("/**");
                    ZoneLog.info(CLASS_NAME, "addInterceptors",
                            "zone-security 签名校验已注册, timestampWindow(全局默认): {}",
                            properties.getSign().getTimestampWindow());
                }
            }
        };
    }
}
