package com.ai.cs.open.util;

import com.ai.cs.common.dto.ToolInvokeDTO;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * 工具调用参数快照与幂等键生成。
 *
 * <p>两处用途：</p>
 * <ol>
 *   <li>落 {@code request_json}：确认页展示 + 判断「同一请求是否已有待确认记录」，避免 AI 每轮重问都新增一条；</li>
 *   <li>生成幂等键：确认那一刻才写入，保证同一意图重复抽取得到同一个键。</li>
 * </ol>
 *
 * <p><b>为什么用 TreeMap</b>：字段顺序必须稳定，否则同样的请求两次序列化得到不同字符串，
 * 既无法比对复用，也会让幂等键漂移。</p>
 */
public final class ToolInvokeSnapshot {

    /** 与 DDL 中 idempotency_key VARCHAR(128) 对齐 */
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;

    private ToolInvokeSnapshot() {
    }

    /**
     * 生成稳定的参数快照 JSON（只保留非空字段，字段按字典序输出）。
     */
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
     * <p>规范化<b>排除</b> {@code idempotencyKey} 与 {@code confirmed}：
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
        // 三者都取满 64 位时可能超过列宽。退化为「租户 + 整体哈希」，
        // 仍保证同租户内可复现、且不截断导致不同请求撞键
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
            // SHA-1 是 JDK 必须实现的算法，走到这里说明运行环境异常
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }
}
