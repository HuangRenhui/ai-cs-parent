package com.ai.cs.workorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 工单服务启动类
 *
 * @author huangrenhui
 * @date 2026/6/11 18:15
 */
@SpringBootApplication(scanBasePackages = "com.ai.cs", exclude = SecurityAutoConfiguration.class)
@EnableDiscoveryClient
public class WorkOrderApplication {

    /**
     * 工单服务入口；排除 Security 自动装配，接口鉴权由网关统一处理
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkOrderApplication.class, args);
    }
}