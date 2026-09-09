package com.ai.cs.knowledge.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus配置类
 * 注册分页插件等拦截器，注意Bean名称带knowledge前缀以避免与其他模块的同类Bean冲突
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 构建MyBatis-Plus拦截器Bean（分页插件）
     */
    @Bean
    public MybatisPlusInterceptor knowledgeMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页内部拦截器，指定数据库类型为MySQL，使Page分页查询自动生成LIMIT语句
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
