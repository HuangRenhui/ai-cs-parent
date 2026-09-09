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

    /**
     * 定时任务服务入口；通过 @EnableScheduling 开启调度，排除 Security 自动装配以避免内部任务接口被鉴权拦截
     */
    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
