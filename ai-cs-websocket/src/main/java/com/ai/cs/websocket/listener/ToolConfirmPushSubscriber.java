package com.ai.cs.websocket.listener;

import com.ai.cs.websocket.endpoint.ChatWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 跨实例推送订阅者。
 *
 * <p>报文约定 {@code sessionId + "\n" + payload}：只按第一个换行切分，
 * payload 原样透传，不在这一层解析业务字段。</p>
 *
 * @author huangrenhui
 * @date 2026-11-25
 */
@Slf4j
@Component
public class ToolConfirmPushSubscriber implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            String raw = new String(message.getBody(), StandardCharsets.UTF_8);
            int idx = raw.indexOf('\n');
            if (idx <= 0 || idx >= raw.length() - 1) {
                log.warn("跨实例推送报文格式不合法，已丢弃 length={}", raw.length());
                return;
            }
            String sessionId = raw.substring(0, idx).trim();
            String payload = raw.substring(idx + 1);
            if (sessionId.isEmpty() || payload.isEmpty()) {
                log.warn("跨实例推送报文缺少 sessionId 或 payload，已丢弃");
                return;
            }
            ChatWebSocket.deliverLocal(sessionId, payload);
        } catch (Exception e) {
            log.warn("跨实例推送投递失败", e);
        }
    }
}
