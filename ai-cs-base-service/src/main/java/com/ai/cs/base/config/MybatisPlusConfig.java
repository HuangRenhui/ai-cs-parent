package com.ai.cs.base.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册 MySQL 分页插件，保证 /customer/page、/session/page 真正带 LIMIT。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器；Bean 名加 base 前缀，避免与其他模块同类型 Bean 冲突。
     */
    @Bean
    public MybatisPlusInterceptor baseMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
