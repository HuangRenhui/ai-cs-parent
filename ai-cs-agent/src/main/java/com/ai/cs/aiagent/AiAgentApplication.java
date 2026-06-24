package com.ai.cs.aiagent;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:53
 * @description TODO
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class AiAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
