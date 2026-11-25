package com.ai.cs.websocket.config;

import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.websocket.endpoint.ChatWebSocket;
import com.ai.cs.websocket.listener.ToolConfirmPushSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * WebSocket 跨实例推送配置
 *
 * <p>订阅 {@code RedisKeyConst.WS_PUSH_CHANNEL}（报文 {@code sessionId\npayload}），
 * 命中本机连接后交给 {@link ChatWebSocket#deliverLocal} 投递。</p>
 */
@Slf4j
@Configuration
public class WsPushConfig {

    @Bean
    public RedisMessageListenerContainer wsPushContainer(RedisConnectionFactory factory,
                                                         ToolConfirmPushSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisKeyConst.WS_PUSH_CHANNEL));
        log.info("WebSocket 跨实例推送已订阅 channel={}", RedisKeyConst.WS_PUSH_CHANNEL);
        return container;
    }
}
