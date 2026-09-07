package com.ai.cs.common.llm;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型用量/失败事件记录器：写入 Redis 流，由 ai-cs-job 消费落库。
 * 不阻塞主调用链，失败仅打日志。
 *
 * @author ai-cs
 */
@Slf4j
@Component
public class ModelUsageRecorder {

    public static final String STREAM_KEY = "ai:model:usage:stream";
    private static final long MAX_LEN = 100000;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 记录一次调用（成功或失败）
     */
    public void record(ModelUsageEvent event) {
        if (event == null || redisTemplate == null) {
            return;
        }
        try {
            if (event.getTs() == null) {
                event.setTs(System.currentTimeMillis());
            }
            Map<String, String> map = new HashMap<>();
            map.put("payload", JSON.toJSONString(event));
            redisTemplate.opsForStream().add(STREAM_KEY, map);
            redisTemplate.opsForStream().trim(STREAM_KEY, MAX_LEN);
        } catch (Exception e) {
            log.warn("记录模型用量事件失败: {}", e.getMessage());
        }
    }

    /**
     * 便捷构造成功事件
     */
    public static ModelUsageEvent success(AiModelRoute route, String sessionId, ModelCallResult result) {
        ModelUsageEvent e = new ModelUsageEvent();
        fill(e, route, sessionId);
        e.setSuccess(1);
        if (result != null) {
            e.setPromptTokens(result.getPromptTokens());
            e.setCompletionTokens(result.getCompletionTokens());
            e.setTotalTokens(result.getTotalTokens());
            e.setLatencyMs(result.getLatencyMs());
        }
        return e;
    }

    /**
     * 便捷构造失败事件
     */
    public static ModelUsageEvent failure(AiModelRoute route, String sessionId, Long latencyMs, String errorMsg) {
        ModelUsageEvent e = new ModelUsageEvent();
        fill(e, route, sessionId);
        e.setSuccess(0);
        e.setLatencyMs(latencyMs);
        e.setErrorMsg(errorMsg == null ? "unknown" : (errorMsg.length() > 480 ? errorMsg.substring(0, 480) : errorMsg));
        return e;
    }

    private static void fill(ModelUsageEvent e, AiModelRoute route, String sessionId) {
        if (route != null) {
            e.setModelId(route.getId());
            e.setModelName(route.getModelName());
            e.setModelType(route.getModelType());
            e.setProvider(route.getProvider());
        }
        e.setSessionId(sessionId);
    }
}
