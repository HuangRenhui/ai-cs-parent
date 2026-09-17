package com.ai.cs.open.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.open.entity.InboundWebhook;
import com.ai.cs.open.entity.OutboundWebhook;
import com.ai.cs.open.service.WebhookService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Webhook 管理控制器。
 */
@RestController
@RequestMapping("/open/webhook")
public class WebhookController {

    @Resource
    private WebhookService webhookService;

    // ==================== 入站 Webhook ====================

    /**
     * 查询全部入站 Webhook。
     */
    @GetMapping("/inbound/list")
    public Result<List<InboundWebhook>> listInboundWebhooks() {
        return Result.success(webhookService.listInboundWebhooks());
    }

    /**
     * 按租户查询入站 Webhook。
     */
    @GetMapping("/inbound/listByTenant")
    public Result<List<InboundWebhook>> listInboundWebhooksByTenant(@RequestParam String tenantCode) {
        return Result.success(webhookService.listInboundWebhooksByTenant(tenantCode));
    }

    /**
     * 新增或更新入站 Webhook。
     */
    @PostMapping("/inbound/save")
    public Result<Void> saveInboundWebhook(@RequestBody InboundWebhook webhook) {
        webhookService.saveInboundWebhook(webhook);
        return Result.success();
    }

    /**
     * 删除入站 Webhook。
     */
    @DeleteMapping("/inbound/delete/{id}")
    public Result<Void> deleteInboundWebhook(@PathVariable Long id) {
        webhookService.deleteInboundWebhook(id);
        return Result.success();
    }

    /**
     * 启用/停用入站 Webhook。
     */
    @PutMapping("/inbound/enable/{id}")
    public Result<Void> setInboundWebhookEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        webhookService.setInboundWebhookEnabled(id, enabled);
        return Result.success();
    }

    // ==================== 出站 Webhook ====================

    /**
     * 查询全部出站 Webhook。
     */
    @GetMapping("/outbound/list")
    public Result<List<OutboundWebhook>> listOutboundWebhooks() {
        return Result.success(webhookService.listOutboundWebhooks());
    }

    /**
     * 按租户查询出站 Webhook。
     */
    @GetMapping("/outbound/listByTenant")
    public Result<List<OutboundWebhook>> listOutboundWebhooksByTenant(@RequestParam String tenantCode) {
        return Result.success(webhookService.listOutboundWebhooksByTenant(tenantCode));
    }

    /**
     * 新增或更新出站 Webhook。
     */
    @PostMapping("/outbound/save")
    public Result<Void> saveOutboundWebhook(@RequestBody OutboundWebhook webhook) {
        webhookService.saveOutboundWebhook(webhook);
        return Result.success();
    }

    /**
     * 删除出站 Webhook。
     */
    @DeleteMapping("/outbound/delete/{id}")
    public Result<Void> deleteOutboundWebhook(@PathVariable Long id) {
        webhookService.deleteOutboundWebhook(id);
        return Result.success();
    }

    /**
     * 启用/停用出站 Webhook。
     */
    @PutMapping("/outbound/enable/{id}")
    public Result<Void> setOutboundWebhookEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        webhookService.setOutboundWebhookEnabled(id, enabled);
        return Result.success();
    }

    /**
     * 手动触发出站 Webhook（测试用 / 内部 Feign 调用）。
     */
    @PostMapping("/outbound/trigger")
    public Result<Void> triggerOutboundWebhook(@RequestParam String eventType,
                                                 @RequestParam(required = false, defaultValue = "default") String tenantCode,
                                                 @RequestBody Map<String, Object> payload) {
        webhookService.triggerOutboundWebhook(eventType, tenantCode, payload);
        return Result.success();
    }

    /**
     * 入站 Webhook 接收端：外部系统按注册 path 推送事件。
     * 路径示例：POST /open/webhook/inbound/receive/webhook/logistics
     */
    @PostMapping("/inbound/receive/**")
    public Result<Map<String, Object>> receiveInbound(HttpServletRequest request,
                                                      @RequestBody(required = false) String body) {
        String uri = request.getRequestURI();
        String marker = "/inbound/receive";
        int idx = uri.indexOf(marker);
        String path = idx >= 0 ? uri.substring(idx + marker.length()) : uri;
        Map<String, String> headers = new java.util.HashMap<>();
        java.util.Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        InboundWebhook webhook = webhookService.receiveInbound(path, headers, body == null ? "" : body);
        return Result.success(Map.of(
                "eventType", webhook.getEventType(),
                "tenantCode", webhook.getTenantCode() == null ? "" : webhook.getTenantCode(),
                "accepted", true
        ));
    }
}
