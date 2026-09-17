package com.ai.cs.open.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：为本模块注册 MySQL 分页插件。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器 Bean；Bean 名加 open 前缀，避免与其他模块的同类型 Bean 冲突。
     */
    @Bean
    public MybatisPlusInterceptor openMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
