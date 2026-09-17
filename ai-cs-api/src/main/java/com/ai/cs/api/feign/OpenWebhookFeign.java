package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.OpenWebhookFeignFallback;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 出站 Webhook Feign：会话生命周期事件异步推送给开放层。
 */
@FeignClient(value = "ai-cs-open", url = "${feign.open.url:http://localhost:8086}",
        fallback = OpenWebhookFeignFallback.class, contextId = "openWebhookFeign")
public interface OpenWebhookFeign {

    /**
     * 触发出站 Webhook（内部调用，失败不影响会话主流程）
     */
    @PostMapping("/open/webhook/outbound/trigger")
    Result<Void> trigger(@RequestParam("eventType") String eventType,
                         @RequestParam("tenantCode") String tenantCode,
                         @RequestBody Map<String, Object> payload);
}
