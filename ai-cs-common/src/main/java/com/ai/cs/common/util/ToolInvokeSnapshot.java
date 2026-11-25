package com.ai.cs.common.util;

import com.ai.cs.common.dto.ToolInvokeDTO;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * 工具调用参数快照与幂等键生成（agent 与 open 共用的落点）。
 *
 * <p>用 TreeMap 保证字段顺序稳定：同样请求两次序列化必须得到同一字符串，
 * 否则无法比对复用，幂等键也会漂移。</p>
 */
public final class ToolInvokeSnapshot {

    /** 与 DDL 中 idempotency_key VARCHAR(128) 对齐 */
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;

    private ToolInvokeSnapshot() {
    }

    /** 生成稳定的参数快照 JSON（只保留非空字段，字段按字典序输出）。 */
    public static String of(ToolInvokeDTO dto) {
        TreeMap<String, Object> map = new TreeMap<>();
        putIfPresent(map, "toolName", dto.getToolName());
        putIfPresent(map, "intentBind", dto.getIntentBind());
        putIfPresent(map, "entityId", dto.getEntityId());
        putIfPresent(map, "entityType", dto.getEntityType());
        putIfPresent(map, "sessionId", dto.getSessionId());
        putIfPresent(map, "payload", dto.getPayload());
        putIfPresent(map, "idempotencyKey", dto.getIdempotencyKey());
        putIfPresent(map, "requestedBy", dto.getRequestedBy());
        return JSON.toJSONString(map);
    }

    /**
     * 生成幂等键：{@code {tenant}:{sessionId}:{toolName}:{sha1(规范化参数)}}。
     *
     * <p>规范化排除 {@code idempotencyKey} 与 {@code confirmed}：
     * 前者是结果不是输入，后者在确认链路上必然为 true，都不该影响键值。</p>
     */
    public static String idempotencyKey(String tenantCode, ToolInvokeDTO dto) {
        TreeMap<String, Object> map = new TreeMap<>();
        putIfPresent(map, "entityId", dto.getEntityId());
        putIfPresent(map, "entityType", dto.getEntityType());
        putIfPresent(map, "payload", dto.getPayload());
        String normalized = JSON.toJSONString(map);

        String tenant = StringUtils.hasText(tenantCode) ? tenantCode.trim() : "default";
        String session = StringUtils.hasText(dto.getSessionId()) ? dto.getSessionId().trim() : "-";
        String tool = StringUtils.hasText(dto.getToolName()) ? dto.getToolName().trim() : "-";

        String key = tenant + ":" + session + ":" + tool + ":" + sha1(normalized);
        if (key.length() <= IDEMPOTENCY_KEY_MAX_LENGTH) {
            return key;
        }
        // 超长时退化为「租户前缀 + 整体哈希」，避免截断导致不同请求撞键
        String shortTenant = tenant.substring(0, Math.min(tenant.length(), 40));
        return shortTenant + ":" + sha1(key);
    }

    private static void putIfPresent(TreeMap<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value.trim());
        }
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }
}
