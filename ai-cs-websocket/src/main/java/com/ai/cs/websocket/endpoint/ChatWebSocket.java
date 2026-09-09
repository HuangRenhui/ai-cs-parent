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

    /** 在线会话表：sessionId -> WebSocket 连接，用于消息推送与连接管理 */
    private static final Map<String, Session> ONLINE_SESSION = new ConcurrentHashMap<>();
    // @ServerEndpoint 实例由 WebSocket 容器管理（非 Spring 单例），依赖通过 setter 注入到静态字段共享
    private static RedisTemplate<String, String> redisTemplate;
    private static AiAgentFeign aiAgentFeign;
    private static SessionFeign sessionFeign;

    /** 注入 Redis（setter 注入到静态字段，原因见上） */
    @Resource
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        ChatWebSocket.redisTemplate = redisTemplate;
    }

    /** 注入 AI 智能体 Feign 客户端 */
    @Resource
    public void setAiAgentFeign(AiAgentFeign aiAgentFeign) {
        ChatWebSocket.aiAgentFeign = aiAgentFeign;
    }

    /** 注入会话服务 Feign 客户端 */
    @Resource
    public void setSessionFeign(SessionFeign sessionFeign) {
        ChatWebSocket.sessionFeign = sessionFeign;
    }

    /**
     * 连接建立：校验 token（查询参数）→ 登记在线会话 → 初始化会话上下文 → 下发连接成功提示
     *
     * @param sessionId 路径参数中的会话 ID
     * @param session   WebSocket 连接
     */
    @OnOpen
    public void onOpen(@PathParam("sessionId") String sessionId, Session session) {
        // 握手必须带合法 token，防止伪造 sessionId 窃听/冒用他人会话
        String token = firstQuery(session, "token");
        if (!JwtUtil.validateToken(token)) {
            log.warn("WebSocket 拒绝未授权连接 sessionId={}", sessionId);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized"));
            } catch (Exception ignored) {
            }
            return;
        }
        // 把用户身份挂到连接属性上，后续收发消息免重复解析 token
        session.getUserProperties().put("userId", JwtUtil.getUserId(token));
        session.getUserProperties().put("username", JwtUtil.getUsername(token));
        session.getUserProperties().put("tokenType", JwtUtil.getTokenType(token));
        // 同一会话重复连接时关闭旧连接，保证一个 sessionId 只有一条活跃连接
        Session previous = ONLINE_SESSION.put(sessionId, session);
        if (previous != null && previous.isOpen() && previous != session) {
            try {
                previous.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "replaced"));
            } catch (Exception ignored) {
            }
        }
        bindMdc(session, sessionId);
        try {
            // 初始化 Redis 会话上下文（不存在才写），供 AI 对话携带历史
            if (redisTemplate != null && redisTemplate.opsForValue().get(RedisKeyConst.CHAT_CONTEXT + sessionId) == null) {
                redisTemplate.opsForValue().set(RedisKeyConst.CHAT_CONTEXT + sessionId, "", 30, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            // Redis 异常不影响连接建立，仅记日志
            log.warn("初始化会话上下文失败 sessionId={}", sessionId, e);
        } finally {
            MDC.clear();
        }
        sendMessage(sessionId, jsonPayload("system", "会话已连接", "", false, null));
    }

    /**
     * 收到客户端消息：解析消息 → 取历史上下文 → 落库用户消息 → 调 AI 服务 → 更新上下文 → 落库 AI 回复 → 推送回复
     *
     * @param payload   客户端发送的原始报文（纯文本或 JSON）
     * @param sessionId 路径参数中的会话 ID
     */
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
            // 读取 Redis 中的对话历史，作为 AI 回复的上下文
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

            // 用户消息先落库，即使后续 AI 调用失败也保留提问记录
            persistMessage(sessionId, customerId, userMsg, MsgTypeEnum.USER.getCode());

            // 调用 AI 智能体服务生成回复；失败时给出兜底文案，保证连接侧始终有响应
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
                    // Feign 降级/业务失败：优先透传失败信息
                    aiReply = aiResult != null && StringUtils.hasText(aiResult.getMsg())
                            ? aiResult.getMsg() : "抱歉，AI服务异常";
                }
            } catch (Exception e) {
                log.error("调用AI智能体失败 sessionId={}", sessionId, e);
                aiReply = "抱歉，AI服务暂时不可用，请稍后重试或转人工。";
            }

            // 把本轮问答追加进上下文并续期 30 分钟
            history += "AI客服：" + aiReply + "\n";
            try {
                if (redisTemplate != null) {
                    redisTemplate.opsForValue().set(RedisKeyConst.CHAT_CONTEXT + sessionId, history, 30, TimeUnit.MINUTES);
                }
            } catch (Exception e) {
                log.warn("更新会话上下文失败 sessionId={}", sessionId, e);
            }
            // AI 回复落库并推送给客户端（携带意图、是否转人工、引用来源供前端渲染）
            persistMessage(sessionId, customerId, aiReply, MsgTypeEnum.AI.getCode());
            sendMessage(sessionId, jsonPayload("ai", aiReply, intent, transferred, citations));
        } finally {
            MDC.clear();
        }
    }

    /**
     * 连接关闭：从在线表中移除（仅当表内仍是当前连接，避免误删新连接）
     */
    @OnClose
    public void onClose(@PathParam("sessionId") String sessionId, Session session) {
        ONLINE_SESSION.remove(sessionId, session);
    }

    /**
     * 连接异常：记录日志并尽力推送错误提示给客户端
     */
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

    /**
     * 向指定会话推送消息：加锁发送，防止并发写同一连接导致帧交错
     */
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

    /**
     * 通过会话服务把消息落库；服务不可用时仅记日志，不阻断聊天主流程
     */
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

    /**
     * 绑定日志上下文：优先使用客户端带来的 requestId，否则新生成，便于按会话串联日志
     */
    private static void bindMdc(Session session, String sessionId) {
        String requestId = session == null ? null : firstQuery(session, "requestId");
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put("requestId", requestId);
        MDC.put("traceId", requestId);
        MDC.put("sessionId", sessionId);
    }

    /**
     * 从握手请求查询参数中取第一个值
     */
    private static String firstQuery(Session session, String name) {
        if (session == null || session.getRequestParameterMap() == null) {
            return null;
        }
        List<String> values = session.getRequestParameterMap().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * 解析用户消息：兼容 JSON 报文（msg/content 字段）与纯文本两种格式
     */
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
                // JSON 解析失败时按纯文本处理
                return raw;
            }
        }
        return raw;
    }

    /**
     * 解析客户 ID：访客令牌直接取令牌中的用户 ID；其余从 JSON 报文的 customerId 字段取，缺省为 0
     */
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

    /**
     * 解析租户编码：从 JSON 报文的 tenantCode 字段取，缺省为 default
     */
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

    /**
     * 组装下行消息 JSON：type 区分 system/ai/error，附意图、是否转人工与知识引用
     */
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
