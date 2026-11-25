package com.ai.cs.open.service;

import com.ai.cs.common.constant.RedisKeyConst;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 确认单状态推送：把状态变更通过 Redis 频道广播给各 WebSocket 实例。
 *
 * <p>报文约定 {@code sessionId + "\n" + JSON}，与
 * {@code WsPushConfig} / {@code ToolConfirmPushSubscriber} 保持一致。</p>
 *
 * <p>推送是尽力而为：任何异常只记 warn，绝不影响确认事务提交。</p>
 *
 * @author huangrenhui
 * @date 2026-11-25
 */
@Slf4j
@Service
public class ToolConfirmNotifier {

    /** 过期事件与结果事件用不同 type，前端据此决定置为 EXPIRED 还是展示结果文案 */
    private static final String TYPE_EXPIRED = "tool-confirm-expired";
    private static final String TYPE_RESULT = "tool-confirm-result";

    /** 推送开关：关掉后仅落库，前端靠轮询/重新进入页面刷新 */
    @Value("${open.tool.confirm-push-enabled:true}")
    private boolean pushEnabled;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 推送确认单状态变更。
     *
     * @param sessionId 会话 ID，为空则不推送（无会话的确认单只能在待确认页看到）
     * @param confirmId 确认单 ID
     * @param status    变更后的状态，取值见 {@code ToolInvokeStatus}
     * @param content   给用户看的文案，可为空由前端兜底
     */
    public void notifyStatus(String sessionId, Long confirmId, String status, String content) {
        if (!pushEnabled || !StringUtils.hasText(sessionId) || !StringUtils.hasText(status)) {
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "EXPIRED".equalsIgnoreCase(status) ? TYPE_EXPIRED : TYPE_RESULT);
            payload.put("confirmId", confirmId);
            payload.put("status", status.trim().toUpperCase());
            if (StringUtils.hasText(content)) {
                payload.put("content", content);
            }
            // 第一个 \n 之前是 sessionId，之后原样透传给连接，订阅端不再解析
            String message = sessionId.trim() + "\n" + payload.toJSONString();
            stringRedisTemplate.convertAndSend(RedisKeyConst.WS_PUSH_CHANNEL, message);
        } catch (Exception e) {
            // 不打印 payload，避免把业务参数写进日志
            log.warn("确认单状态推送失败 confirmId={} status={} sessionId={}",
                    confirmId, status, sessionId);
        }
    }
}
