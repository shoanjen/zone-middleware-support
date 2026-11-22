package com.zone.middleware.web.config;

import com.zone.middleware.web.advice.ZoneGlobalExceptionAdvice;
import com.zone.middleware.web.advice.ZoneMethodValidationAdvice;
import com.zone.middleware.web.filter.ZoneTraceInboundFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;

/**
 * Web 入站支撑自动装配：两个能力均可选、默认关——
 * <ul>
 *   <li>全局异常处理器：{@code zone.middleware.web.exception-advice.enabled=true} 开启；</li>
 *   <li>Trace 入站 Filter：{@code zone.middleware.web.trace-filter.enabled=true} 开启。</li>
 * </ul>
 * 非 web 类路径（无 spring-webmvc/servlet）时整类跳过，非 web 消费方零影响。
 */
@AutoConfiguration
@ConditionalOnClass({RestControllerAdvice.class, Filter.class, WebMvcConfigurer.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ZoneWebProperties.class)
public class ZoneWebInboundAutoConfiguration {

    /**
     * 全局异常处理器（可选）：仅在 exception-advice 开关开启时装配，
     * 低优先级让位上游自定义 advice。
     *
     * @return 全局异常处理器
     */
    @Bean
    @ConditionalOnProperty(prefix = "zone.middleware.web", name = "exception-advice-enabled",
            havingValue = "true")
    public ZoneGlobalExceptionAdvice zoneGlobalExceptionAdvice() {
        return new ZoneGlobalExceptionAdvice();
    }

    /**
     * 方法级校验处理器（可选，Boot 3.2+ 路径）：处理 Spring MVC 内建方法校验异常
     * {@code HandlerMethodValidationException}（该类型仅存在于 Spring 6.1+，故以
     * 类名级 @ConditionalOnClass 隔离——Boot 3.1 及以下不装配本 Bean、不内省目标类，
     * 避免 NoClassDefFoundError 崩溃；3.1 的 @Validated 经典路径由全局处理器覆盖）。
     *
     * @return 方法级校验处理器
     */
    @Bean
    @ConditionalOnProperty(prefix = "zone.middleware.web", name = "exception-advice-enabled",
            havingValue = "true")
    @ConditionalOnClass(name = "org.springframework.web.method.annotation.HandlerMethodValidationException")
    public ZoneMethodValidationAdvice zoneMethodValidationAdvice() {
        return new ZoneMethodValidationAdvice();
    }

    /**
     * Trace 入站 Filter 注册（可选）：仅在 trace-filter 开关开启时注册，
     * order 取最高优先级，先于鉴权等业务过滤器，确保其日志已有链路标识。
     *
     * @param properties web 入站配置（reqId 随机段长度）
     * @return Filter 注册器
     */
    @Bean
    @ConditionalOnProperty(prefix = "zone.middleware.web", name = "trace-filter-enabled",
            havingValue = "true")
    public FilterRegistrationBean<ZoneTraceInboundFilter> zoneTraceInboundFilterRegistration(
            ZoneWebProperties properties) {
        FilterRegistrationBean<ZoneTraceInboundFilter> registration =
                new FilterRegistrationBean<>(new ZoneTraceInboundFilter(properties.getReqIdLength()));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("zoneTraceInboundFilter");
        return registration;
    }
}
