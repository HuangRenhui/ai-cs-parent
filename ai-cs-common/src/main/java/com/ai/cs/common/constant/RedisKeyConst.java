package com.ai.cs.common.constant;

/**
 * Redis Key 常量
 *
 * @author huangrenhui
 * @date 2026/6/11 17:41
 */
public class RedisKeyConst {
    // 聊天上下文前缀
    public static final String CHAT_CONTEXT = "chat:context:";
    // 会话在线标记
    public static final String CHAT_ONLINE = "chat:online:";

    // ===== AI 模型注册管理（跨服务共享，base-service 写入，各消费服务读取）=====
    // Hash：field = model_type(LLM/EMBEDDING/...)，value = 该能力下全部启用模型的 JSON 数组
    public static final String AI_MODEL_REGISTRY = "ai:model:registry";
    // Hash：field = model_type，value = 当前生效模型的 JSON
    public static final String AI_MODEL_ACTIVE = "ai:model:active";
    // 字符串：最新变更版本号，消费服务用于判断是否需要刷新本地缓存
    public static final String AI_MODEL_VERSION = "ai:model:version";
}
