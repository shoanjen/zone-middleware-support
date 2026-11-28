package com.zone.middleware.database.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 整合：默认装配分页拦截器（方言运行期按连接自动识别）；
 * Mapper 扫描遵循官方行为——主应用包下的 {@code @Mapper} 接口由官方 MP 自动装配扫描，
 * 扫描包之外的场景由上游标准 {@code @MapperScan} 声明，本类不提供扫描键。
 * 上游自定义 {@code MybatisPlusInterceptor} Bean 即整体接管（{@code @ConditionalOnMissingBean} 让位）。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class ZoneMyBatisPlusConfiguration {

    /**
     * 装配 MyBatis-Plus 分页拦截器（上游自定义 MybatisPlusInterceptor Bean 时让位）。
     *
     * @return 含分页内插件的拦截器（方言运行期按连接自动识别）
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor zoneMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 无参构造：运行期按连接 URL 自动识别方言（MySQL/H2 等）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
