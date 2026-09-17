package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.OpenWebhookFeign;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 出站 Webhook Feign 降级：开放层不可用时吞掉事件，不阻断会话。
 */
@Slf4j
@Component
public class OpenWebhookFeignFallback implements OpenWebhookFeign {

    /** 降级：仅记日志，会话创建/结束仍然成功 */
    @Override
    public Result<Void> trigger(String eventType, String tenantCode, Map<String, Object> payload) {
        log.warn("出站 Webhook 触发失败，已降级 eventType={} tenant={}", eventType, tenantCode);
        return Result.fail(503, "开放层暂时不可用");
    }
}
