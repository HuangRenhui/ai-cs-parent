package com.ai.cs.ops;

import com.ai.cs.ops.config.OpsProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 运营中心服务启动类：提供健康探测、日志/链路查询、告警等运营能力。
 * 当前为纯「内存 + 本地文件」实现，无数据库与 Redis 依赖，故排除相关自动装配。
 */
@SpringBootApplication(
        scanBasePackages = {"com.ai.cs.ops", "com.ai.cs.common"},
        exclude = {
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class,
                DataSourceAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                SecurityAutoConfiguration.class
        }
)
@EnableDiscoveryClient
@EnableConfigurationProperties(OpsProperties.class)
public class OpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsApplication.class, args);
    }
}
