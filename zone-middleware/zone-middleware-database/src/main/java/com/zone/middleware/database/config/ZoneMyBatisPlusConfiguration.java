package com.zone.middleware.database.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * MyBatis-Plus 整合：分页插件默认装配（可关），mapper-package 配置存在时自动扫描注册 Mapper。
 */
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@AutoConfigureAfter(ZoneDataSourceAutoConfiguration.class)
@EnableConfigurationProperties(ZoneDatabaseProperties.class)
public class ZoneMyBatisPlusConfiguration {

    /**
     * 装配 MyBatis-Plus 分页插件。
     *
     * @return 含分页内插件的拦截器（方言运行期按连接自动识别）
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    @ConditionalOnProperty(
            prefix = "zone.middleware.db.mybatis",
            name = "pagination-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public MybatisPlusInterceptor zoneMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 无参构造：运行期按连接 URL 自动识别方言（MySQL/H2 等）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    /**
     * 注册 Mapper 扫描 Registrar（静态 Bean，早于配置类实例化）。
     *
     * @return Mapper 扫描注册器
     */
    @Bean
    public static ZoneMapperScannerRegistrar zoneMapperScannerRegistrar() {
        return new ZoneMapperScannerRegistrar();
    }

    static class ZoneMapperScannerRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

        private Environment environment;

        /**
         * 设置应用环境。
         *
         * @param environment 应用环境
         */
        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        /**
         * mapper-package 已配置时注册 MapperScannerConfigurer。
         *
         * @param registry Bean 定义注册表
         */
        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            String mapperPackage = environment.getProperty("zone.middleware.db.mybatis.mapper-package");
            if (mapperPackage == null || mapperPackage.trim().isEmpty()) {
                return;
            }
            BeanDefinitionBuilder builder =
                    BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
            builder.addPropertyValue("basePackage", mapperPackage.trim());
            builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            registry.registerBeanDefinition("zoneMapperScannerConfigurer", builder.getBeanDefinition());
        }

        /** 无需处理：仅做注册表阶段的 Mapper 注册。 */
        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            // 无需处理：仅做注册表阶段的 Mapper 注册
        }
    }
}
