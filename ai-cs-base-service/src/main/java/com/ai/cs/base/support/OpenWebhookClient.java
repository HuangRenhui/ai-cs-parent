package com.ai.cs.base.support;

import com.ai.cs.common.security.InternalAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 调用开放层出站 Webhook（占位）
 *
 * <p>TODO 后续实现：向 {@code {feign.open.url}/open/webhook/outbound/trigger?eventType=..&tenantCode=..}
 * POST 事件负载，带内部鉴权头 {@code InternalAuthProperties.HEADER}（避免被下游鉴权拦截）；
 * 不用 Feign 是为避免 base-service 依赖 ai-cs-api。失败只记日志，不阻断业务。</p>
 *
 * <p>当前不发请求：{@link #trigger} 只记日志，因此会话生命周期事件不会推送到开放层。</p>
 */
@Slf4j
@Component
public class OpenWebhookClient {

    @Value("${feign.open.url:http://localhost:8086}")
    private String openBaseUrl;

    @Resource
    private InternalAuthProperties internalAuthProperties;

    /**
     * 触发出站 Webhook（占位：不发请求）
     *
     * @param eventType  事件类型
     * @param tenantCode 租户编码
     * @param payload    事件负载
     */
    public void trigger(String eventType, String tenantCode, Map<String, Object> payload) {
        log.info("[占位] 出站 Webhook 触发未实现，不发请求 eventType={} tenantCode={}", eventType, tenantCode);
    }
}
