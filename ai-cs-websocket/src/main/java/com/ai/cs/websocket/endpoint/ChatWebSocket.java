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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 聊天端点（消息处理为占位）。
 *
 * <p>握手不带 token（避免令牌出现在 URL / 访问日志里）；连接建立后 5 秒内必须发送首帧
 * {@code {"type":"auth","token":"..."}} 完成鉴权，否则连接被关闭。鉴权与归属校验在首帧完成。</p>
 *
 * <p>TODO 后续实现消息处理链路：取 Redis 历史上下文 → 用户消息落库 → 调 ai-agent 生成回复
 * → 追加并续期上下文 → AI 回复落库 → 推送回复（携带意图、是否转人工、引用来源、接入坐席）。</p>
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sessionId}")
public class ChatWebSocket {

    private static final int MAX_ONLINE_SESSIONS = 2000;

    /** 首帧鉴权超时：超时未带 token 的连接直接断开，避免匿名连接占坑 */
    private static final int AUTH_TIMEOUT_SECONDS = 5;

    /** 单条消息字符上限 */
    private static final int MAX_MESSAGE_CHARS = 64 * 1024;

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
            // 队列满时直接拒绝，避免业务处理回落到 WS IO 线程
            new ThreadPoolExecutor.AbortPolicy());

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
            closeQuietly(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "max connections reached");
            return;
        }
        // 连接先置为未鉴权：token 只允许走首帧，不进 URL（URL 会落在访问日志、Referer、浏览器历史里）
        session.getUserProperties().put("authed", Boolean.FALSE);
        session.getUserProperties().put("sessionId", sessionId);
        CLEANUP_EXECUTOR.schedule(() -> {
            if (session.isOpen() && !Boolean.TRUE.equals(session.getUserProperties().get("authed"))) {
                log.warn("WebSocket 鉴权超时，关闭连接 sessionId={}", sessionId);
                closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "auth timeout");
            }
        }, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sendDirect(session, jsonPayload("auth-required", "请先完成鉴权", "", false, null));
    }

    /**
     * 收到客户端消息（占位：入队后仅记日志）
     *
     * @param payload   客户端发送的原始报文（纯文本或 JSON）
     * @param sessionId 路径参数中的会话 ID
     */
    @OnMessage
    public void onMessage(String payload, @PathParam("sessionId") String sessionId, Session session) {
        // 未鉴权时，首帧必须是 {"type":"auth","token":"..."}；鉴权成功后才会进入消息队列
        if (!Boolean.TRUE.equals(session.getUserProperties().get("authed"))) {
            tryAuth(payload, sessionId, session);
            return;
        }
        // 心跳直接回 pong，不走线程池，避免心跳排队
        if (isHeartbeat(payload)) {
            deliverLocal(sessionId, "{\"type\":\"pong\"}");
            return;
        }
        if (payload != null && payload.length() > MAX_MESSAGE_CHARS) {
            log.warn("WebSocket 消息超长，已拒绝 sessionId={} length={}", sessionId, payload.length());
            sendDirect(session, jsonPayload("error", "消息过长，请分段发送", "", false, null));
            return;
        }
        // 入队异步处理，避免阻塞 WS 容器线程
        try {
            MSG_EXECUTOR.execute(() -> handleMessage(payload, sessionId, session));
        } catch (RejectedExecutionException e) {
            // 队列满时返回错误提示
            log.warn("WebSocket 消息队列已满，拒绝本次消息 sessionId={}", sessionId);
            sendDirect(session, jsonPayload("error", "当前消息较多，请稍后重试", "", false, null));
        }
    }

    /**
     * 首帧鉴权：校验 token → 会话归属 → 登记在线表 → 提示已连接。
     */
    private void tryAuth(String payload, String sessionId, Session session) {
        String token = parseAuthToken(payload);
        if (token == null) {
            log.warn("WebSocket 首帧不是合法鉴权报文 sessionId={}", sessionId);
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "auth required");
            return;
        }
        // 握手必须带合法 token，防止伪造 sessionId 窃听/冒用他人会话
        if (!JwtUtil.validateToken(token)) {
            log.warn("WebSocket 拒绝未授权连接 sessionId={}", sessionId);
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
            return;
        }
        // 校验 token 是否有权访问该 sessionId，防止合法令牌窃听他人会话
        if (!authorizeSession(sessionId, token)) {
            log.warn("WebSocket 拒绝越权连接 sessionId={} userId={}", sessionId, JwtUtil.getUserId(token));
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "session forbidden");
            return;
        }
        // 把用户身份挂到连接属性上，后续收发消息免重复解析 token
        session.getUserProperties().put("authed", Boolean.TRUE);
        session.getUserProperties().put("userId", JwtUtil.getUserId(token));
        session.getUserProperties().put("username", JwtUtil.getUsername(token));
        session.getUserProperties().put("tokenType", JwtUtil.getTokenType(token));
        // 访客令牌用户名为 visitor:{visitorRef}，后续落库与鉴权都用这个标识
        String visitorRef = extractVisitorRef(token);
        if (StringUtils.hasText(visitorRef)) {
            session.getUserProperties().put("visitorRef", visitorRef);
        }
        // 同一会话重复连接时关闭旧连接，保证一个 sessionId 只有一条活跃连接
        Session previous = ONLINE_SESSION.put(sessionId, session);
        if (previous != null && previous.isOpen() && previous != session) {
            closeQuietly(previous, CloseReason.CloseCodes.NORMAL_CLOSURE, "replaced");
        }
        bindMdc(session, sessionId);
        try {
            sendDirect(session, jsonPayload("system", "会话已连接", "", false, null));
        } finally {
            MDC.clear();
        }
    }

    /** 解析首帧鉴权报文；格式非法或超长直接返回 null */
    private static String parseAuthToken(String payload) {
        if (!StringUtils.hasText(payload) || payload.length() > 4096) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(payload);
            if (json == null || !"auth".equalsIgnoreCase(json.getString("type"))) {
                return null;
            }
            String token = json.getString("token");
            return StringUtils.hasText(token) ? token.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 心跳报文：{"type":"ping"}，只做字符串包含判断，避免为心跳解析 JSON */
    private static boolean isHeartbeat(String payload) {
        return StringUtils.hasText(payload) && payload.length() <= 128 && payload.contains("\"ping\"");
    }

    /**
     * 实际处理客户端消息（占位）
     *
     * <p>TODO 后续实现：解析报文（兼容 JSON 的 msg/content 与纯文本）→ 取 Redis 历史上下文
     * → 用户消息落库 → 调 {@code aiAgentFeign.chat} 生成回复（失败给兜底文案）
     * → 上下文追加并续期 30 分钟 → AI 回复落库 → 推送回复。当前<b>既不调用 AI、也不落库、也不推送</b>。</p>
     */
    private void handleMessage(String payload, String sessionId, Session session) {
        // 在线程池线程内重新绑定 MDC，保证日志可串联
        bindMdc(session, sessionId);
        try {
            log.info("[占位] WebSocket 消息处理（未调用 AI、未落库、未推送）sessionId={}", sessionId);
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
            sendDirect(session, content);
        }
    }

    /** 直接向连接写文本（不查在线表），用于鉴权与心跳这类注册前的下行 */
    private static void sendDirect(Session session, String content) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.getBasicRemote().sendText(content);
            }
        } catch (Exception e) {
            log.error("推送消息失败", e);
        }
    }

    /** 关闭连接：连接已关闭等异常只记 debug，不向上抛 */
    private static void closeQuietly(Session session, CloseReason.CloseCode code, String reason) {
        try {
            if (session != null && session.isOpen()) {
                session.close(new CloseReason(code, reason));
            }
        } catch (Exception e) {
            log.debug("关闭连接异常 reason={}", reason, e);
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
