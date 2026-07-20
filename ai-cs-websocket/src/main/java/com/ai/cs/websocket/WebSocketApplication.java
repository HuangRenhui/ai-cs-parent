package com.ai.cs.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * WebSocket服务启动类
 *
 * @author huangrenhui
 * @date 2026/6/11 17:49
 */
@SpringBootApplication(scanBasePackages = "com.ai.cs")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ai.cs.api.feign")
public class WebSocketApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebSocketApplication.class, args);
    }
}
