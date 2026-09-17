package com.ai.cs.common.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.Locale;
import java.util.Set;

/**
 * 管理员操作审计：把 URI 翻成可读的「谁做了什么」，并脱敏请求体。
 */
public final class AuditOperationSupport {

    /** 密码、密钥类字段不落审计 */
    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "apikey", "api_key", "token", "secret",
            "authorization", "accesskey", "access_key"
    );

    private static final int MAX_PARAMS = 2000;

    private AuditOperationSupport() {
    }

    /**
     * 是否记审计：只记管理员后台写操作；聊天、Widget、入站回调、探活不记。
     */
    public static boolean shouldAudit(String method, String uri) {
        if (uri == null || !isMutating(method)) {
            return false;
        }
        String path = uri.toLowerCase(Locale.ROOT);
        if (path.startsWith("/actuator")
                || path.startsWith("/files/")
                || path.startsWith("/webjars/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/help-center")
                || path.startsWith("/open/widget")
                || path.startsWith("/open/webhook/inbound/receive")
                || path.startsWith("/ai/")
                || path.startsWith("/system/log")
                || path.contains("/auth/login/platform")) {
            return false;
        }
        return true;
    }

    /** POST/PUT/DELETE/PATCH 视为写操作 */
    public static boolean isMutating(String method) {
        if (method == null) {
            return false;
        }
        String m = method.toUpperCase(Locale.ROOT);
        return "POST".equals(m) || "PUT".equals(m) || "DELETE".equals(m) || "PATCH".equals(m);
    }

    /**
     * 模块编码，供后台筛选。/system/user 记成 user，而不是笼统的 system。
     */
    public static String moduleOf(String uri) {
        if (uri == null || uri.length() < 2) {
            return "unknown";
        }
        String[] parts = uri.split("/");
        if (parts.length > 2 && "system".equals(parts[1])) {
            String second = parts[2];
            if (second.startsWith("ai-model")) {
                return "model";
            }
            if (second.startsWith("data-retention")) {
                return "retention";
            }
            if (second.startsWith("intent")) {
                return "intent";
            }
            if (second.startsWith("slot")) {
                return "slot";
            }
            return second;
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            return parts[1];
        }
        return "unknown";
    }

    /**
     * 可读操作名，例如「删除客户」「保存知识库」「管理员登录」。
     */
    public static String operationOf(String method, String uri) {
        String module = moduleLabel(moduleOf(uri));
        String path = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if (path.contains("/auth/login")) {
            return "管理员登录";
        }
        if (path.contains("/auth/logout")) {
            return "退出登录";
        }
        String verb = verbOf(method, path);
        return verb + module;
    }

    /** 模块英文码转中文，未知则原样返回 */
    public static String moduleLabel(String module) {
        if (module == null) {
            return "";
        }
        return switch (module) {
            case "auth" -> "登录";
            case "user" -> "用户";
            case "role" -> "角色";
            case "menu" -> "菜单";
            case "config" -> "配置";
            case "customer" -> "客户";
            case "workorder" -> "工单";
            case "session" -> "会话";
            case "knowledge" -> "知识库";
            case "agent" -> "坐席";
            case "open" -> "开放接入";
            case "model" -> "模型";
            case "intent" -> "意图";
            case "slot" -> "填槽";
            case "retention" -> "数据保留";
            case "ops" -> "运维";
            default -> module;
        };
    }

    /**
     * 请求体脱敏：密码/密钥打码，超长截断。
     */
    public static String redactParams(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                Object parsed = JSON.parse(trimmed);
                redactNode(parsed);
                trimmed = JSON.toJSONString(parsed);
            } catch (Exception ignored) {
                // 非 JSON 原样截断
            }
        }
        if (trimmed.length() > MAX_PARAMS) {
            return trimmed.substring(0, MAX_PARAMS) + "…";
        }
        return trimmed;
    }

    /** 从已脱敏 JSON 里尽量取出 username，用于登录尚未写入 JWT 的场景 */
    public static String usernameFromParams(String params) {
        if (params == null || params.isBlank() || !params.trim().startsWith("{")) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(params);
            String name = obj.getString("username");
            return name == null || name.isBlank() ? null : name.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String verbOf(String method, String path) {
        if (path.contains("/delete")) {
            return "删除";
        }
        if (path.contains("/export")) {
            return "导出";
        }
        if (path.contains("/import")) {
            return "导入";
        }
        if (path.contains("/test") || path.contains("/invoke") || path.contains("/ping")) {
            return "测试";
        }
        if (path.contains("/active") || path.contains("/activate")) {
            return "设为生效";
        }
        if (path.contains("/enabled") || path.contains("/enable")) {
            return "启停";
        }
        if (path.contains("/assign")) {
            return "分配";
        }
        if (path.contains("/close")) {
            return "关闭";
        }
        if (path.contains("/complete")) {
            return "完成";
        }
        if (path.contains("/vectorize")) {
            return "向量化";
        }
        if (path.contains("/update") || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            return "更新";
        }
        if (path.contains("/create") || path.contains("/save") || path.contains("/add")) {
            return "保存";
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return "删除";
        }
        return "操作";
    }

    private static void redactNode(Object node) {
        if (node instanceof JSONObject obj) {
            for (String key : obj.keySet()) {
                if (isSecretKey(key)) {
                    obj.put(key, "***");
                } else {
                    redactNode(obj.get(key));
                }
            }
        }
    }

    private static boolean isSecretKey(String key) {
        if (key == null) {
            return false;
        }
        return SECRET_KEYS.contains(key.toLowerCase(Locale.ROOT).replace("-", ""));
    }
}
