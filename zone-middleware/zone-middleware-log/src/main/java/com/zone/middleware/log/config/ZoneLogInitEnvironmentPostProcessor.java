package com.zone.middleware.log.config;

import com.zone.middleware.log.ZoneLogSettings;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 日志设置早期应用：在环境准备阶段绑定 zone.middleware.log.*（appName 回退 spring.application.name），
 * 使 ZoneLog 在任何 Bean 初始化日志之前即按最终配置输出。
 */
public class ZoneLogInitEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 环境准备阶段绑定日志配置并应用到全局设置。
     * <p>绑定异常时保持默认设置，不阻塞应用启动。</p>
     *
     * @param environment 应用环境
     * @param application 启动应用实例
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            ZoneLogProperties properties = Binder.get(environment)
                    .bind("zone.middleware.log", Bindable.of(ZoneLogProperties.class))
                    .orElseGet(ZoneLogProperties::new);
            String appName = properties.getAppName();
            if (appName == null || appName.isEmpty()) {
                appName = environment.getProperty("spring.application.name");
            }
            ZoneLogSettings.apply(appName, properties.getLevel(),
                    properties.isIncludeTraceId(), properties.isIncludeAppName(),
                    ZoneLogEnvResolver.resolveEnv(properties.getEnv(), environment),
                    properties.getFormat());
        } catch (Exception ignored) {
            // 绑定异常时保持默认设置，不阻塞应用启动（非法 format 由 AutoConfiguration 绑定阶段失败）
        }
    }

    /**
     * 获取执行顺序：尽量早执行，先于其他依赖日志设置的环境处理。
     *
     * @return 优先级序数
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
