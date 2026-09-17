package com.ai.cs.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI智能体服务启动类
 *
 * @author huangrenhui
 * @date 2026/6/11 17:53
 */
@SpringBootApplication(scanBasePackages = "com.ai.cs", exclude = SecurityAutoConfiguration.class)
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ai.cs.api.feign")
public class AiAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
