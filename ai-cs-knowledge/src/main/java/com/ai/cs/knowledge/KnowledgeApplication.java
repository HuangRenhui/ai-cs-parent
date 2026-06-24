package com.ai.cs.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 知识库服务启动类
 * 集成LangChain4j RAG框架，提供智能问答功能
 *
 * @author huangrenhui
 * @date 2026/6/11 17:57
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties
public class KnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}