package com.ai.cs.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 基础服务启动类
 *
 * @author huangrenhui
 * @date 2026/6/11 18:10
 */
@SpringBootApplication(scanBasePackages = "com.ai.cs")
@EnableDiscoveryClient
@EnableAsync
public class BaseServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BaseServiceApplication.class, args);
    }
}
