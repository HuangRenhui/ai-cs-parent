package com.ai.cs.websocket.endpoint;

import com.ai.cs.api.feign.AiAgentFeign;
import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.result.Result;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 聊天端点
 *
 * @author huangrenhui
 * @date 2026/6/11 16:47
 */
@Component
@ServerEndpoint("/ws/{sessionId}")
public class ChatWebSocket {

    private static final Map<String, Session> ONLINE_SESSION = new ConcurrentHashMap<>();
    private static RedisTemplate<String, String> redisTemplate;
    private static AiAgentFeign aiAgentFeign;

    @Resource
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        ChatWebSocket.redisTemplate = redisTemplate;
    }

    @Resource
    public void setAiAgentFeign(AiAgentFeign aiAgentFeign) {
        ChatWebSocket.aiAgentFeign = aiAgentFeign;
    }

    @OnOpen
    public void onOpen(@PathParam("sessionId") String sessionId, Session session) {
        ONLINE_SESSION.put(sessionId, session);
        // 初始化会话上下文，有效期30分钟
        redisTemplate.opsForValue().set(RedisKeyConst.CHAT_CONTEXT + sessionId, "", 30, TimeUnit.MINUTES);
    }

    @OnMessage
    public void onMessage(String userMsg, @PathParam("sessionId") String sessionId) {
        // 1. 获取历史对话上下文
        String history = redisTemplate.opsForValue().get(RedisKeyConst.CHAT_CONTEXT + sessionId);
        history = history == null ? "" : history;
        history += "用户：" + userMsg + "\n";

        // 2. 调用AI智能体服务
        ChatDTO dto = new ChatDTO();
        dto.setSessionId(sessionId);
        dto.setMsg(userMsg);
        dto.setHistory(history);
        Result<String> aiResult = aiAgentFeign.chat(dto);
        String aiReply = aiResult.getData() == null ? "抱歉，AI服务异常" : aiResult.getData();

        // 3. 更新上下文
        history += "AI客服：" + aiReply + "\n";
        redisTemplate.opsForValue().set(RedisKeyConst.CHAT_CONTEXT + sessionId, history, 30, TimeUnit.MINUTES);

        // 4. 推送给前端
        sendMessage(sessionId, aiReply);
    }

    @OnClose
    public void onClose(@PathParam("sessionId") String sessionId) {
        ONLINE_SESSION.remove(sessionId);
        redisTemplate.delete(RedisKeyConst.CHAT_CONTEXT + sessionId);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
    }

    /** 推送消息 */
    private void sendMessage(String sessionId, String content) {
        Session session = ONLINE_SESSION.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(content);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}