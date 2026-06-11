package com.ai.cs.aiagent.config;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:54
 * @description LLM 配置属性类
 */

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.llm")
public class LlmProperties {
    private String apiKey;
    private String apiSecret;
    private String model;
    private String url;
}
