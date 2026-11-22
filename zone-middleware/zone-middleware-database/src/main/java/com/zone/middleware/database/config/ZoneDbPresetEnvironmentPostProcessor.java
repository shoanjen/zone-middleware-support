package com.zone.middleware.database.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 部署模式预设注入：读取 zone.middleware.db.mode，按 LOCAL / CLOUD 各注入一套官方键
 * （spring.datasource.hikari.* / spring.datasource.druid.*）预设值——属性源以 addLast 最低优先级
 * 注入，上游显式配置（application.yml/环境变量/系统属性）总是优先，未配置项才吃预设。
 * mode 非法值不在此报错，留给装配期快速失败。
 */
public class ZoneDbPresetEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PRESET_SOURCE_NAME = "zoneDbPresets";

    /**
     * 按模式注入 db 官方键预设（hikari 与 druid 两组同时注入，未被选中的池无消费者、无副作用）。
     *
     * @param environment 应用环境
     * @param application 启动应用实例
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty("zone.middleware.db.mode");
        boolean cloud = mode != null && "CLOUD".equalsIgnoreCase(mode.trim());

        Map<String, Object> presets = new HashMap<>();
        if (cloud) {
            // HikariCP 云端预设：更长获取超时、连接保活 120s、关闭泄漏检测
            presets.put("spring.datasource.hikari.connection-timeout", "15000");
            presets.put("spring.datasource.hikari.keepalive-time", "120000");
            presets.put("spring.datasource.hikari.leak-detection-threshold", "0");
            // Druid 云端预设：更长获取等待、开启连接保活
            presets.put("spring.datasource.druid.max-wait", "15000");
            presets.put("spring.datasource.druid.keep-alive", "true");
        } else {
            // HikariCP 本地预设：短获取超时、开启泄漏检测便于开发排查、关闭保活
            presets.put("spring.datasource.hikari.connection-timeout", "5000");
            presets.put("spring.datasource.hikari.keepalive-time", "0");
            presets.put("spring.datasource.hikari.leak-detection-threshold", "10000");
            // Druid 本地预设：短获取等待、关闭连接保活
            presets.put("spring.datasource.druid.max-wait", "5000");
            presets.put("spring.datasource.druid.keep-alive", "false");
        }
        // 与模式无关的 Druid 池默认值（两模式一致）
        presets.put("spring.datasource.druid.initial-size", "2");
        presets.put("spring.datasource.druid.min-idle", "2");
        presets.put("spring.datasource.druid.max-active", "8");
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
