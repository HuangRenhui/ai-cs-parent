package com.ai.cs.open;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 开放平台服务启动类：对外开放接入能力（Widget 初始化、行业包、连接器、开放工具）的独立部署入口。
 * 本模块不使用 Redis 与安全框架，故排除对应自动装配，保持最小依赖。
 */
@SpringBootApplication(
        scanBasePackages = {"com.ai.cs.open", "com.ai.cs.common"},
        exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class, SecurityAutoConfiguration.class}
)
@EnableDiscoveryClient
@MapperScan("com.ai.cs.open.mapper")
public class OpenApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenApplication.class, args);
    }
}
