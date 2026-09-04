package com.ai.cs.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务服务启动类
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@SpringBootApplication(scanBasePackages = "com.ai.cs", exclude = SecurityAutoConfiguration.class)
@EnableDiscoveryClient
@EnableScheduling
public class JobApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
