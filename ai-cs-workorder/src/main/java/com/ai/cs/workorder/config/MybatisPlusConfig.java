package com.ai.cs.workorder.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：工单分页查询必须带 LIMIT，避免全表拉取。
 */
@Configuration
public class MybatisPlusConfig {

    /** 分页拦截器；Bean 名加 workorder 前缀避免冲突 */
    @Bean
    public MybatisPlusInterceptor workorderMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
