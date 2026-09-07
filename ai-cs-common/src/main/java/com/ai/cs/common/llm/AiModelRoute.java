package com.ai.cs.common.llm;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI 模型路由描述：注册中心与 Router 之间交换的轻量模型信息。
 * 与 base-service 的 {@code AiModelConfig} 字段一一对应，避免 Router 依赖实体。
 *
 * @author ai-cs
 */
@Data
public class AiModelRoute implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 id */
    private Long id;

    /** 显示名称 */
    private String modelName;

    /** 供应方 ollama/dashscope/openai/deepseek/other */
    private String provider;

    /** 能力 LLM/EMBEDDING/RERANK/VISION/MULTIMODAL */
    private String modelType;

    /** 服务地址(OpenAI 兼容 base url，例如 http://localhost:11434/v1) */
    private String baseUrl;

    /** 密钥 */
    private String apiKey;

    /** 附加密钥 */
    private String apiSecret;

    /** 上游模型标识 */
    private String remoteModel;

    /** 温度 */
    private BigDecimal temperature;

    /** embedding 维度 */
    private Integer dimension;

    /** 故障切换优先级，越小越优先 */
    private Integer priority;

    /** 单次调用超时(毫秒) */
    private Integer timeoutMs;

    /** 同模型失败重试次数(不含首次) */
    private Integer maxRetries;

    /** 连续失败熔断阈值(达到即摘除) */
    private Integer failThreshold;

    /** 每日 token 配额(NULL/0 不限) */
    private Long dailyTokenLimit;

    /** 每日成本配额(元，NULL 不限) */
    private BigDecimal dailyCostLimit;

    /** 输入单价(元/千token) */
    private BigDecimal costPer1kIn;

    /** 输出单价(元/千token) */
    private BigDecimal costPer1kOut;

    /** 是否启用 1/0 */
    private Integer enabled;

    /** 是否当前生效 1/0(同能力仅一条) */
    private Integer isActive;

    /** 健康状态 UNKNOWN/HEALTHY/DOWN */
    private String health;
}
