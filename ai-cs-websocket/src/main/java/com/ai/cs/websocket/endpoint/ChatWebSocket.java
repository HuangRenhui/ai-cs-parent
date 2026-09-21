package com.ai.cs.websocket.endpoint;

import com.ai.cs.api.feign.AiAgentFeign;
import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.ChatReplyDTO;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.dto.SessionSnapshotDTO;
import com.ai.cs.common.enums.MsgTypeEnum;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.util.JwtUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 聊天端点（消息处理为占位）。
 *
 * <p>握手必须带 token（查询参数），禁止只靠猜 sessionId；连接建立后的鉴权与归属校验已实现。</p>
 *
 * <p>TODO 后续实现消息处理链路：取 Redis 历史上下文 → 用户消息落库 → 调 ai-agent 生成回复
 * → 追加并续期上下文 → AI 回复落库 → 推送回复（携带意图、是否转人工、引用来源、接入坐席）。</p>
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sessionId}")
public class ChatWebSocket {

    private static final int MAX_ONLINE_SESSIONS = 2000;

    /** 在线会话表：sessionId -> WebSocket 连接，用于消息推送与连接管理 */
    private static final Map<String, Session> ONLINE_SESSION = new ConcurrentHashMap<>();

    /** 聊天消息处理线程池：避免在 WS IO 线程上同步阻塞 Feign/LLM */
    private static final ExecutorService MSG_EXECUTOR = new ThreadPoolExecutor(
            16, 64, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000),
            r -> {
                Thread t = new Thread(r, "ws-msg");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    private static final ScheduledExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-session-cleanup");
        t.setDaemon(true);
        return t;
    });

    static {
        CLEANUP_EXECUTOR.scheduleAtFixedRate(() -> {
            int before = ONLINE_SESSION.size();
            ONLINE_SESSION.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().isOpen());
            int removed = before - ONLINE_SESSION.size();
            if (removed > 0) {
                log.info("清理已关闭的WebSocket连接 {} 个，当前在线 {}", removed, ONLINE_SESSION.size());
            }
        }, 10, 60, TimeUnit.SECONDS);
    }
    // @ServerEndpoint 实例由 WebSocket 容器管理（非 Spring 单例），依赖通过 setter 注入到静态字段共享
    private static RedisTemplate<String, String> redisTemplate;
    private static StringRedisTemplate stringRedisTemplate;
    private static AiAgentFeign aiAgentFeign;
    private static SessionFeign sessionFeign;

    /** 注入 Redis（setter 注入到静态字段，原因见上） */
    @Resource
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        ChatWebSocket.redisTemplate = redisTemplate;
    }

    /** 注入用于跨实例推送的 StringRedisTemplate */
    @Resource
    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        ChatWebSocket.stringRedisTemplate = stringRedisTemplate;
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
     * 连接建立：校验 token（查询参数）→ 越权校验 → 登记在线会话 → 下发连接成功提示
     *
     * @param sessionId 路径参数中的会话 ID
     * @param session   WebSocket 连接
     */
    @OnOpen
    public void onOpen(@PathParam("sessionId") String sessionId, Session session) {
        if (ONLINE_SESSION.size() >= MAX_ONLINE_SESSIONS) {
            log.warn("WebSocket 连接数已达上限 {}，拒绝新连接 sessionId={}", MAX_ONLINE_SESSIONS, sessionId);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "max connections reached"));
            } catch (Exception e) {
                log.debug("关闭超限连接异常 sessionId={}", sessionId, e);
            }
            return;
        }
        // 握手必须带合法 token，防止伪造 sessionId 窃听/冒用他人会话
        String token = firstQuery(session, "token");
        if (!JwtUtil.validateToken(token)) {
            log.warn("WebSocket 拒绝未授权连接 sessionId={}", sessionId);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized"));
            } catch (Exception e) {
                log.debug("关闭未授权连接异常 sessionId={}", sessionId, e);
            }
            return;
        }
        // 把用户身份挂到连接属性上，后续收发消息免重复解析 token
        session.getUserProperties().put("userId", JwtUtil.getUserId(token));
        session.getUserProperties().put("username", JwtUtil.getUsername(token));
        session.getUserProperties().put("tokenType", JwtUtil.getTokenType(token));
        // 访客令牌用户名为 visitor:{visitorRef}，后续落库与鉴权都用这个标识
        String visitorRef = extractVisitorRef(token);
        if (StringUtils.hasText(visitorRef)) {
            session.getUserProperties().put("visitorRef", visitorRef);
        }
        // 校验 token 是否有权访问该 sessionId，防止合法令牌窃听他人会话
        if (!authorizeSession(sessionId, token)) {
            log.warn("WebSocket 拒绝越权连接 sessionId={} userId={}", sessionId, JwtUtil.getUserId(token));
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "session forbidden"));
            } catch (Exception e) {
                log.debug("关闭越权连接异常 sessionId={}", sessionId, e);
            }
            return;
        }
        // 同一会话重复连接时关闭旧连接，保证一个 sessionId 只有一条活跃连接
        Session previous = ONLINE_SESSION.put(sessionId, session);
        if (previous != null && previous.isOpen() && previous != session) {
            try {
                previous.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "replaced"));
            } catch (Exception e) {
                log.debug("关闭旧连接异常 sessionId={}", sessionId, e);
            }
        }
        bindMdc(session, sessionId);
        try {
            sendMessage(sessionId, jsonPayload("system", "会话已连接", "", false, null));
        } finally {
            MDC.clear();
        }
    }

    /**
     * 收到客户端消息（占位：入队后仅记日志）
     *
     * @param payload   客户端发送的原始报文（纯文本或 JSON）
     * @param sessionId 路径参数中的会话 ID
     */
    @OnMessage
    public void onMessage(String payload, @PathParam("sessionId") String sessionId) {
        // 入队异步处理，避免阻塞 WS 容器线程
        MSG_EXECUTOR.execute(() -> handleMessage(payload, sessionId));
    }

    /**
     * 实际处理客户端消息（占位）
     *
     * <p>TODO 后续实现：解析报文（兼容 JSON 的 msg/content 与纯文本）→ 取 Redis 历史上下文
     * → 用户消息落库 → 调 {@code aiAgentFeign.chat} 生成回复（失败给兜底文案）
     * → 上下文追加并续期 30 分钟 → AI 回复落库 → 推送回复。当前<b>既不调用 AI、也不落库、也不推送</b>。</p>
     */
    private void handleMessage(String payload, String sessionId) {
        log.info("[占位] WebSocket 消息处理（未调用 AI、未落库、未推送）sessionId={}", sessionId);
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
        } catch (Exception e) {
            log.debug("推送错误提示异常", e);
        }
    }

    /**
     * 向指定会话推送消息：优先走 Redis Pub/Sub，本机与其他节点统一由订阅端投递，避免双发。
     * Redis 不可用时回退本机直推。
     */
    private void sendMessage(String sessionId, String content) {
        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.convertAndSend(RedisKeyConst.WS_PUSH_CHANNEL, sessionId + "\n" + content);
                return;
            }
        } catch (Exception e) {
            log.warn("WS 跨节点推送失败，回退本机 sessionId={}: {}", sessionId, e.getMessage());
        }
        deliverLocal(sessionId, content);
    }

    /**
     * 本机投递：仅当当前 JVM 持有该 session 的连接时发送。供 Redis 订阅回调使用。
     */
    public static void deliverLocal(String sessionId, String content) {
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
     * 校验 token 是否有权访问会话：访客只能连自己的会话；员工可连任意会话。
     * 会话尚未落库时放行（首条消息会 ensure）；会话服务失败或熔断则拒绝，避免把「查不到」当成「不存在」。
     */
    private boolean authorizeSession(String sessionId, String token) {
        // Feign 未注入：本地单测或未装配会话客户端，无法校验归属，只能放行
        if (sessionFeign == null) {
            log.warn("会话 Feign 未注入，跳过归属校验 sessionId={}", sessionId);
            return true;
        }
        try {
            Result<SessionSnapshotDTO> res = sessionFeign.snapshot(sessionId);
            // 空响应或业务失败（含 503 熔断）：服务不可用，拒绝连接，防止窃听窗口被打开
            if (res == null || !res.isOk()) {
                log.warn("会话快照不可用，拒绝连接 sessionId={} msg={}",
                        sessionId, res == null ? "empty" : res.getMsg());
                return false;
            }
            // 成功且 data 为空：会话确实还不存在，首条消息会 ensure，允许新会话握手
            if (res.getData() == null) {
                return true;
            }
            SessionSnapshotDTO snap = res.getData();
            if (JwtUtil.isVisitor(token)) {
                Long userId = JwtUtil.getUserId(token);
                Long customerId = snap.getCustomerId();
                String visitorRef = extractVisitorRef(token);
                // 已绑定客户：令牌 userId 必须等于会话 customerId
                if (customerId != null && customerId > 0L) {
                    return customerId.equals(userId);
                }
                // 匿名会话：必须 visitorRef 一致，禁止猜 sessionId 窃听其他匿名访客
                if (StringUtils.hasText(snap.getVisitorRef()) && StringUtils.hasText(visitorRef)) {
                    return snap.getVisitorRef().equals(visitorRef);
                }
                return customerId == null || customerId == 0L;
            }
            return true;
        } catch (Exception e) {
            // 超时、网络异常与熔断一样视为服务失败，拒绝而不是放行
            log.warn("会话归属校验异常，拒绝连接 sessionId={}: {}", sessionId, e.getMessage());
            return false;
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
     * 从访客 JWT 用户名解析 visitorRef。员工令牌返回 null。
     */
    private static String extractVisitorRef(String token) {
        if (!JwtUtil.isVisitor(token)) {
            return null;
        }
        String username = JwtUtil.getUsername(token);
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return username.startsWith("visitor:") ? username.substring("visitor:".length()) : username;
    }

    /**
     * 组装下行消息 JSON：type 区分 system/ai/error，附意图、是否转人工、知识引用与接入坐席
     */
    private String jsonPayload(String type, String content, String intent, boolean transferred, Object citations) {
        return jsonPayload(type, content, intent, transferred, citations, null, null);
    }

    /**
     * 带坐席工号/姓名的下行消息，转人工成功时聊天页据此渲染接待卡
     */
    private String jsonPayload(String type, String content, String intent, boolean transferred,
                               Object citations, String agentNo, String agentName) {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("content", content);
        json.put("intent", intent);
        json.put("transferred", transferred);
        if (citations != null) {
            json.put("citations", citations);
        }
        if (StringUtils.hasText(agentNo)) {
            json.put("agentNo", agentNo);
        }
        if (StringUtils.hasText(agentName)) {
            json.put("agentName", agentName);
        }
        return json.toJSONString();
    }
}
