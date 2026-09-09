package com.ai.cs.websocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置
 *
 * @author huangrenhui
 * @date 2026/6/11 16:44
 */
@Configuration
public class WebSocketConfig {
    /**
     * 注册 ServerEndpointExporter，使 @ServerEndpoint 注解的端点被自动扫描并暴露
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}