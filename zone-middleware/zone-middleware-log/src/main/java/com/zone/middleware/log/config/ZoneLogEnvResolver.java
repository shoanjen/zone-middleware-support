package com.zone.middleware.log.config;

import org.springframework.core.env.Environment;

/**
 * 环境标识回退解析：zone.middleware.log.env → spring.profiles.active 首段 → unknown。
 * <p>EPP 环境阶段早期应用与 AutoConfiguration 幂等重放共用本解析，保证两条装配链结果一致。</p>
 */
final class ZoneLogEnvResolver {

    /** 工具类禁止实例化 */
    private ZoneLogEnvResolver() {
    }

    /**
     * 解析生效环境标识。
     *
     * @param configuredEnv 上游显式配置的 env（zone.middleware.log.env），可空
     * @param environment   应用环境（spring.profiles.active 回退来源），可空
     * @return 生效环境标识；全链未命中返回 unknown
     */
    static String resolveEnv(String configuredEnv, Environment environment) {
        if (configuredEnv != null && !configuredEnv.isEmpty()) {
            return configuredEnv;
        }
        String profiles = environment == null ? null : environment.getProperty("spring.profiles.active");
        if (profiles != null && !profiles.isEmpty()) {
            int comma = profiles.indexOf(',');
            String first = comma >= 0 ? profiles.substring(0, comma) : profiles;
            first = first.trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return "unknown";
    }
}
