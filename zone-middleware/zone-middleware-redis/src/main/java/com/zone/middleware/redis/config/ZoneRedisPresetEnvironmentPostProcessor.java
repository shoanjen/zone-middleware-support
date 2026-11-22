package com.zone.middleware.redis.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 部署模式预设注入：读取 zone.middleware.redis.mode，按 LOCAL / CLOUD 各注入一套官方键
 * （spring.data.redis.*）预设值——属性源以 addLast 最低优先级注入，上游显式配置
 * （application.yml/环境变量/系统属性）总是优先，未配置项才吃预设。
 * TCP 保活无官方键，不在本处理器注入（由 ZoneRedisAutoConfiguration 的定制器按模式应用）。
 * mode 非法值不在此报错，留给装配期快速失败。
 */
public class ZoneRedisPresetEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PRESET_SOURCE_NAME = "zoneRedisPresets";

    /**
     * 按模式注入 redis 官方键预设。
     *
     * @param environment 应用环境
     * @param application 启动应用实例
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty("zone.middleware.redis.mode");
        boolean cloud = mode != null && "CLOUD".equalsIgnoreCase(mode.trim());

        Map<String, Object> presets = new HashMap<>();
        // LOCAL 宽松开发预设 / CLOUD 保守预设：更长连接与命令超时（TCP 保活经定制器按模式应用）
        presets.put("spring.data.redis.connect-timeout", cloud ? "10s" : "2s");
        presets.put("spring.data.redis.timeout", cloud ? "10s" : "3s");
        // 连接池默认值（与模式无关）
        presets.put("spring.data.redis.lettuce.pool.max-active", "8");
        presets.put("spring.data.redis.lettuce.pool.max-idle", "8");
        presets.put("spring.data.redis.lettuce.pool.min-idle", "0");
        presets.put("spring.data.redis.lettuce.pool.max-wait", "2s");
        environment.getPropertySources().addLast(new MapPropertySource(PRESET_SOURCE_NAME, presets));
    }

    /**
     * 获取执行顺序：晚于 ConfigData 加载（+10），此时用户配置已可见且优先级恒高于本预设源。
     *
     * @return 优先级序数
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
