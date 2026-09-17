package com.ai.cs.open.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.HmacSignUtil;
import com.ai.cs.common.util.SecretCipherUtil;
import com.ai.cs.open.entity.InboundWebhook;
import com.ai.cs.open.entity.OutboundWebhook;
import com.ai.cs.open.mapper.InboundWebhookMapper;
import com.ai.cs.open.mapper.OutboundWebhookMapper;
import com.ai.cs.open.util.ConnectorUrlGuard;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Webhook 服务：处理入站和出站 Webhook，含 HMAC/Token 鉴权与出站 SSRF 防护。
 */
@Slf4j
@Service
public class WebhookService extends ServiceImpl<InboundWebhookMapper, InboundWebhook> {

    @Resource
    private InboundWebhookMapper inboundWebhookMapper;
    @Resource
    private OutboundWebhookMapper outboundWebhookMapper;

    // ==================== 入站 Webhook ====================

    /**
     * 查询全部入站 Webhook，按 ID 倒序返回。
     */
    public List<InboundWebhook> listInboundWebhooks() {
        return inboundWebhookMapper.selectList(new LambdaQueryWrapper<InboundWebhook>()
                .orderByDesc(InboundWebhook::getId));
    }

    /**
     * 按租户查询入站 Webhook。
     */
    public List<InboundWebhook> listInboundWebhooksByTenant(String tenantCode) {
        return inboundWebhookMapper.selectList(new LambdaQueryWrapper<InboundWebhook>()
                .eq(InboundWebhook::getTenantCode, tenantCode)
                .orderByDesc(InboundWebhook::getId));
    }

    /**
     * 新增或更新入站 Webhook。
     */
    public void saveInboundWebhook(InboundWebhook webhook) {
        if (webhook == null || !StringUtils.hasText(webhook.getName())) {
            throw new BusinessException("Webhook 名称不能为空");
        }
        if (!StringUtils.hasText(webhook.getPath())) {
            throw new BusinessException("Webhook 路径不能为空");
        }
        if (!StringUtils.hasText(webhook.getEventType())) {
            throw new BusinessException("事件类型不能为空");
        }
        // 路径统一格式化，确保以 / 开头
        if (!webhook.getPath().startsWith("/")) {
            webhook.setPath("/" + webhook.getPath());
        }
        encryptAuthConfig(webhook::getAuthConfig, webhook::setAuthConfig);
        if (webhook.getEnabled() == null) {
            webhook.setEnabled(1);
        }
        if (webhook.getId() == null) {
            inboundWebhookMapper.insert(webhook);
        } else {
            inboundWebhookMapper.updateById(webhook);
        }
    }

    /**
     * 删除入站 Webhook（逻辑删除）。
     */
    public void deleteInboundWebhook(Long id) {
        inboundWebhookMapper.deleteById(id);
    }

    /**
     * 启用/停用入站 Webhook。
     */
    public void setInboundWebhookEnabled(Long id, Integer enabled) {
        InboundWebhook webhook = inboundWebhookMapper.selectById(id);
        if (webhook == null) {
            throw new BusinessException("入站 Webhook 不存在");
        }
        webhook.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        inboundWebhookMapper.updateById(webhook);
    }

    /**
     * 根据路径查找启用的入站 Webhook。
     */
    public InboundWebhook findInboundWebhookByPath(String path) {
        String normalized = path == null ? "" : path;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return inboundWebhookMapper.selectOne(new LambdaQueryWrapper<InboundWebhook>()
                .eq(InboundWebhook::getPath, normalized)
                .eq(InboundWebhook::getEnabled, 1)
                .last("limit 1"));
    }

    /**
     * 接收入站事件：按 path 匹配配置 → 校验鉴权 → 返回事件类型供上游处理。
     *
     * @return 匹配到的入站配置
     */
    public InboundWebhook receiveInbound(String path, Map<String, String> headers, String body) {
        InboundWebhook webhook = findInboundWebhookByPath(path);
        if (webhook == null) {
            throw new BusinessException("未注册的入站 Webhook 路径");
        }
        if (!validateInboundWebhookAuth(webhook, headers, body)) {
            throw new BusinessException(401, "入站 Webhook 鉴权失败");
        }
        log.info("入站 Webhook 已接收 path={} eventType={} tenant={}", path, webhook.getEventType(), webhook.getTenantCode());
        return webhook;
    }

    /**
     * 验证入站 Webhook 请求的鉴权（占位）
     *
     * <p>TODO 后续实现：none 直接通过；signature 用 HMAC-SHA256 比对 X-Signature / X-Hub-Signature-256；
     * token 校验 Bearer 或 X-Webhook-Token。配置中的 secret/token 需先解密。</p>
     *
     * <p><b>安全约定</b>：鉴权未实现期间**一律失败关闭**（返回 false → 调用方抛 401），
     * 绝不默认放行。补实现时保持同样的失败关闭语义。</p>
     *
     * @param webhook 入站配置
     * @param headers 请求头
     * @param body    请求体
     * @return 占位恒返回 false（失败关闭）
     */
    public boolean validateInboundWebhookAuth(InboundWebhook webhook, Map<String, String> headers, String body) {
        log.warn("[占位] 入站 Webhook 鉴权未实现，按失败关闭处理 eventType={}",
                webhook == null ? null : webhook.getEventType());
        return false;
    }

    // ==================== 出站 Webhook ====================

    /**
     * 查询全部出站 Webhook，按 ID 倒序返回。
     */
    public List<OutboundWebhook> listOutboundWebhooks() {
        return outboundWebhookMapper.selectList(new LambdaQueryWrapper<OutboundWebhook>()
                .orderByDesc(OutboundWebhook::getId));
    }

    /**
     * 按租户查询出站 Webhook。
     */
    public List<OutboundWebhook> listOutboundWebhooksByTenant(String tenantCode) {
        return outboundWebhookMapper.selectList(new LambdaQueryWrapper<OutboundWebhook>()
                .eq(OutboundWebhook::getTenantCode, tenantCode)
                .orderByDesc(OutboundWebhook::getId));
    }

    /**
     * 按事件类型查询启用的出站 Webhook。
     */
    public List<OutboundWebhook> listOutboundWebhooksByEventType(String eventType, String tenantCode) {
        return outboundWebhookMapper.selectList(new LambdaQueryWrapper<OutboundWebhook>()
                .eq(OutboundWebhook::getEventType, eventType)
                .eq(OutboundWebhook::getTenantCode, tenantCode)
                .eq(OutboundWebhook::getEnabled, 1));
    }

    /**
     * 新增或更新出站 Webhook。
     */
    public void saveOutboundWebhook(OutboundWebhook webhook) {
        if (webhook == null || !StringUtils.hasText(webhook.getName())) {
            throw new BusinessException("Webhook 名称不能为空");
        }
        if (!StringUtils.hasText(webhook.getCallbackUrl())) {
            throw new BusinessException("回调 URL 不能为空");
        }
        if (!StringUtils.hasText(webhook.getEventType())) {
            throw new BusinessException("事件类型不能为空");
        }
        ConnectorUrlGuard.assertSafe(webhook.getCallbackUrl(), webhook.getTenantCode());
        encryptAuthConfig(webhook::getAuthConfig, webhook::setAuthConfig);
        if (webhook.getEnabled() == null) {
            webhook.setEnabled(1);
        }
        if (webhook.getId() == null) {
            outboundWebhookMapper.insert(webhook);
        } else {
            outboundWebhookMapper.updateById(webhook);
        }
    }

    /**
     * 删除出站 Webhook（逻辑删除）。
     */
    public void deleteOutboundWebhook(Long id) {
        outboundWebhookMapper.deleteById(id);
    }

    /**
     * 启用/停用出站 Webhook。
     */
    public void setOutboundWebhookEnabled(Long id, Integer enabled) {
        OutboundWebhook webhook = outboundWebhookMapper.selectById(id);
        if (webhook == null) {
            throw new BusinessException("出站 Webhook 不存在");
        }
        webhook.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        outboundWebhookMapper.updateById(webhook);
    }

    /**
     * 触发出站 Webhook，向订阅方推送事件（占位）
     *
     * <p>TODO 后续实现：按事件类型查启用的出站配置；推送前校验回调 URL 安全性，并使用禁止重定向的
     * 客户端（防 302 跳内网绕过 SSRF）；signature 类型写 X-Signature 的 HMAC，token 类型写 Bearer；
     * 失败只记日志，不抛给调用方。</p>
     *
     * @param eventType  事件类型
     * @param tenantCode 租户编码，为空时按 default 处理
     * @param payload    事件负载
     */
    @Transactional
    public void triggerOutboundWebhook(String eventType, String tenantCode, Map<String, Object> payload) {
        log.info("[占位] 触发出站 Webhook eventType={} tenantCode={}", eventType, tenantCode);
    }

    /** 加密 authConfig 后回写，避免明文落库 */
    private static void encryptAuthConfig(java.util.function.Supplier<String> getter,
                                          java.util.function.Consumer<String> setter) {
        String raw = getter.get();
        if (StringUtils.hasText(raw)) {
            setter.accept(SecretCipherUtil.encrypt(raw));
        }
    }
}
