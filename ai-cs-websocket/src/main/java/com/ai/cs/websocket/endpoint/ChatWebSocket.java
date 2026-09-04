package com.ai.cs.websocket.endpoint;

import com.ai.cs.api.feign.AiAgentFeign;
import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.ChatReplyDTO;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.enums.MsgTypeEnum;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.util.JwtUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 聊天端点。握手必须带 token（查询参数），禁止只靠猜 sessionId。
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sessionId}")
public class ChatWebSocket {

    private static final Map<String, Session> ONLINE_SESSION = new ConcurrentHashMap<>();
    private static RedisTemplate<String, String> redisTemplate;
    private static AiAgentFeign aiAgentFeign;
    private static SessionFeign sessionFeign;

    @Resource
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        ChatWebSocket.redisTemplate = redisTemplate;
    }

    @Resource
    public void setAiAgentFeign(AiAgentFeign aiAgentFeign) {
        ChatWebSocket.aiAgentFeign = aiAgentFeign;
    }

    @Resource
    public void setSessionFeign(SessionFeign sessionFeign) {
        ChatWebSocket.sessionFeign = sessionFeign;
    }

    @OnOpen
    public void onOpen(@PathParam("sessionId") String sessionId, Session session) {
        String token = firstQuery(session, "token");
        if (!JwtUtil.validateToken(token)) {
            log.warn("WebSocket 拒绝未授权连接 sessionId={}", sessionId);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized"));
            } catch (Exception ignored) {
            }
            return;
        }
        session.getUserProperties().put("userId", JwtUtil.getUserId(token));
        session.getUserProperties().put("username", JwtUtil.getUsername(token));
        session.getUserProperties().put("tokenType", JwtUtil.getTokenType(token));
        Session previous = ONLINE_SESSION.put(sessionId, session);
        if (previous != null && previous.isOpen() && previous != session) {
            try {
                previous.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "replaced"));
            } catch (Exception ignored) {
            }
        }
        bindMdc(session, sessionId);
        try {
            if (redisTemplate != null && redisTemplate.opsForValue().get(RedisKeyConst.CHAT_CONTEXT + sessionId) == null) {
                redisTemplate.opsForValue().set(RedisKeyConst.CHAT_CONTEXT + sessionId, "", 30, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("初始化会话上下文失败 sessionId={}", sessionId, e);
        } finally {
            MDC.clear();
        }
        sendMessage(sessionId, jsonPayload("system", "会话已连接", "", false, null));
    }

    @OnMessage
    public void onMessage(String payload, @PathParam("sessionId") String sessionId) {
        Session session = ONLINE_SESSION.get(sessionId);
        bindMdc(session, sessionId);
        try {
            String userMsg = extractUserMsg(payload);
            if (!StringUtils.hasText(userMsg)) {
                sendMessage(sessionId, jsonPayload("error", "消息内容不能为空", "", false, null));
                return;
            }
            Long customerId = extractCustomerId(payload, session);
            String history = "";
            try {
                if (redisTemplate != null) {
                    history = redisTemplate.opsForValue().get(RedisKeyConst.CHAT_CONTEXT + sessionId);
                }
            } catch (Exception e) {
                log.warn("读取会话上下文失败 sessionId={}", sessionId, e);
            }
            history = history == null ? "" : history;
            history += "用户：" + userMsg + "\n";

            persistMessage(sessionId, customerId, userMsg, MsgTypeEnum.USER.getCode());

            String aiReply;
            String intent = "";
            boolean transferred = false;
            Object citations = null;
            try {
                ChatDTO dto = new ChatDTO();
                dto.setSessionId(sessionId);
                dto.setMsg(userMsg);
                dto.setHistory(history);
                dto.setCustomerId(customerId);
                dto.setTenantCode(extractTenantCode(payload));
                Result<ChatReplyDTO> aiResult = aiAgentFeign.chat(dto);
                if (aiResult != null && aiResult.isOk() && aiResult.getData() != null) {
                    aiReply = StringUtils.hasText(aiResult.getData().getReply())
                            ? aiResult.getData().getReply() : "抱歉，AI服务异常";
                    intent = aiResult.getData().getIntent();
                    transferred = aiResult.getData().isTransferred();
                    citations = aiResult.getData().getCitations();
                } else {
                    aiReply = aiResult != null && StringUtils.hasText(aiResult.getMsg())
                            ? aiResult.getMsg() : "抱歉，AI服务异常";
                }
            } catch (Exception e) {
                log.error("调用AI智能体失败 sessionId={}", sessionId, e);
                aiReply = "抱歉，AI服务暂时不可用，请稍后重试或转人工。";
            }

            history += "AI客服：" + aiReply + "\n";
            try {
                if (redisTemplate != null) {
                    redisTemplate.opsForValue().set(RedisKeyConst.CHAT_CONTEXT + sessionId, history, 30, TimeUnit.MINUTES);
                }
            } catch (Exception e) {
                log.warn("更新会话上下文失败 sessionId={}", sessionId, e);
            }
            persistMessage(sessionId, customerId, aiReply, MsgTypeEnum.AI.getCode());
            sendMessage(sessionId, jsonPayload("ai", aiReply, intent, transferred, citations));
        } finally {
            MDC.clear();
        }
    }

    @OnClose
    public void onClose(@PathParam("sessionId") String sessionId, Session session) {
        ONLINE_SESSION.remove(sessionId, session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket异常", throwable);
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(jsonPayload("error", "连接异常，请刷新后重试", "", false, null));
            }
        } catch (Exception ignored) {
        }
    }

    private void sendMessage(String sessionId, String content) {
        Session session = ONLINE_SESSION.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.getBasicRemote().sendText(content);
                }
            } catch (Exception e) {
                log.error("推送消息失败 sessionId={}", sessionId, e);
            }
        }
    }

    private void persistMessage(String sessionId, Long customerId, String content, Integer senderType) {
        if (sessionFeign == null) {
            return;
        }
        try {
            SessionDTO dto = new SessionDTO();
            dto.setSessionId(sessionId);
            dto.setCustomerId(customerId);
            dto.setMsgContent(content);
            dto.setSenderType(senderType);
            dto.setSessionType(1);
            Result<String> saved = sessionFeign.saveMessage(dto);
            if (saved == null || !saved.isOk()) {
                log.error("会话消息落库失败 sessionId={} msg={}", sessionId, saved == null ? "empty" : saved.getMsg());
            }
        } catch (Exception e) {
            log.error("会话消息落库失败 sessionId={}", sessionId, e);
        }
    }

    private static void bindMdc(Session session, String sessionId) {
        String requestId = session == null ? null : firstQuery(session, "requestId");
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put("requestId", requestId);
        MDC.put("traceId", requestId);
        MDC.put("sessionId", sessionId);
    }

    private static String firstQuery(Session session, String name) {
        if (session == null || session.getRequestParameterMap() == null) {
            return null;
        }
        List<String> values = session.getRequestParameterMap().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String extractUserMsg(String payload) {
        if (!StringUtils.hasText(payload)) {
            return "";
        }
        String raw = payload.trim();
        if (raw.startsWith("{")) {
            try {
                JSONObject json = JSON.parseObject(raw);
                return json.getString("msg") != null ? json.getString("msg") : json.getString("content");
            } catch (Exception ignored) {
                return raw;
            }
        }
        return raw;
    }

    private Long extractCustomerId(String payload, Session session) {
        if (session != null && JwtUtil.TYP_VISITOR.equals(session.getUserProperties().get("tokenType"))) {
            Object userId = session.getUserProperties().get("userId");
            if (userId instanceof Long id) {
                return id;
            }
        }
        if (!StringUtils.hasText(payload) || !payload.trim().startsWith("{")) {
            return 0L;
        }
        try {
            Long customerId = JSON.parseObject(payload).getLong("customerId");
            return customerId == null ? 0L : customerId;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String extractTenantCode(String payload) {
        if (!StringUtils.hasText(payload) || !payload.trim().startsWith("{")) {
            return "default";
        }
        try {
            String tenant = JSON.parseObject(payload).getString("tenantCode");
            return StringUtils.hasText(tenant) ? tenant.trim() : "default";
        } catch (Exception e) {
            return "default";
        }
    }

    private String jsonPayload(String type, String content, String intent, boolean transferred, Object citations) {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("content", content);
        json.put("intent", intent);
        json.put("transferred", transferred);
        if (citations != null) {
            json.put("citations", citations);
        }
        return json.toJSONString();
    }
}
