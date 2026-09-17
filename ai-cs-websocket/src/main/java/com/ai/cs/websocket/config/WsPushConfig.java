package com.ai.cs.websocket.config;

import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.websocket.endpoint.ChatWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * WebSocket 跨实例推送配置（占位）
 *
 * <p>TODO 后续实现：订阅 {@code RedisKeyConst.WS_PUSH_CHANNEL} 频道（报文格式 {@code sessionId\npayload}），
 * 把其他节点投递的消息交给 {@link ChatWebSocket#deliverLocal} 送到本机连接。</p>
 *
 * <p>⚠️ 占位实现<strong>不注册任何监听器</strong>，因此跨实例推送不生效；单机部署下本机直推仍可用。</p>
 */
@Slf4j
@Configuration
public class WsPushConfig {

    /**
     * Redis 订阅容器（占位：未注册监听器）
     */
    @Bean
    public RedisMessageListenerContainer wsPushContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        log.info("[占位] WebSocket 跨实例推送订阅容器（未注册监听器）channel={}", RedisKeyConst.WS_PUSH_CHANNEL);
        return container;
    }
}
