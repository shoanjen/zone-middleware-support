package com.zone.middleware.database.config;

import com.alibaba.druid.filter.FilterManager;
import com.alibaba.druid.pool.DruidDataSource;
import com.zone.middleware.common.error.SystemError;
import com.zone.middleware.common.exception.ZoneBusinessException;
import com.zone.middleware.log.ZoneLog;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Druid filter 别名后置校验：官方 druid-spring-boot-3-starter 装配数据源后，对照 Druid
 * 注册表校验 {@code spring.datasource.druid.filters} 别名串。Druid 原生对未注册别名仅打
 * 日志静默跳过，这里快速失败，避免上游配错后监控/防护能力静默丢失。
 *
 * <p>校验时机为数据源 Bean 初始化完成之后（starter 的 DruidDataSourceWrapper 已完成 init），
 * 仍处上下文刷新期，失败同样阻断启动。键面前缀与合法取值均由官方 starter / Druid 注册表承载，
 * 本类只读不改；类路径无 Druid 时整体短路。</p>
 */
@AutoConfiguration
@ConditionalOnClass(DruidDataSource.class)
public class ZoneDruidFilterValidationConfiguration {

    private static final String FILTERS_KEY = "spring.datasource.druid.filters";
    private static final String DRUID_FILTER_ALIAS_KEY_PREFIX = "druid.filters.";

    /**
     * 注册 Druid filter 别名后置校验器（static @Bean：BeanPostProcessor 须早于普通 Bean
     * 实例化，static 化避免过早实例化所在配置类）。
     *
     * @param environment 应用环境（读取 spring.datasource.druid.filters 配置值）
     * @return 别名校验 BeanPostProcessor
     */
    @Bean
    public static BeanPostProcessor zoneDruidFilterAliasValidator(Environment environment) {
        return new ZoneDruidFilterAliasValidator(environment);
    }

    /**
     * Druid filter 别名校验器：对每个经过的 Bean 判定为 Druid 数据源时执行别名对照。
     */
    private static final class ZoneDruidFilterAliasValidator implements BeanPostProcessor {

        private final Environment environment;

        ZoneDruidFilterAliasValidator(Environment environment) {
            this.environment = environment;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof DruidDataSource) {
                validateAliases();
            }
            return bean;
        }

        /**
         * 校验 spring.datasource.druid.filters 别名（逗号分隔）均在 Druid 注册表中。
         *
         * @throws ZoneBusinessException 存在未注册别名（CONFIG_ERROR）或注册表读取失败
         */
        private void validateAliases() {
            String filters = environment.getProperty(FILTERS_KEY);
            if (filters == null || filters.trim().isEmpty()) {
                return;
            }
            Properties registry = loadRegistry();
            for (String alias : filters.split(",")) {
                String trimmed = alias.trim();
                if (!trimmed.isEmpty() && registry.getProperty(DRUID_FILTER_ALIAS_KEY_PREFIX + trimmed) == null) {
                    throw new ZoneBusinessException(SystemError.CONFIG_ERROR,
                            "无效的 " + FILTERS_KEY + " 配置值: " + trimmed
                                    + "，合法取值为 Druid 注册的 filter 别名（stat / wall / slf4j 等），"
                                    + "或改用官方 starter 细粒度键 spring.datasource.druid.filter.<name>.enabled");
                }
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("filters", filters);
            ZoneLog.info("ZoneDruidFilterValidationConfiguration", "validateAliases",
                    "druid filters alias check passed: {}", info);
        }

        /**
         * 读取 Druid 内置 filter 别名注册表（META-INF/druid-filter.properties）。
         *
         * @return 别名注册表（键形如 druid.filters.&lt;alias&gt;）
         * @throws ZoneBusinessException 注册表读取失败（CONFIG_ERROR）
         */
        private Properties loadRegistry() {
            try {
                return FilterManager.loadFilterConfig();
            } catch (IOException e) {
                throw new ZoneBusinessException(SystemError.CONFIG_ERROR, "读取 Druid filter 别名注册表失败", e);
            }
        }
    }
}
