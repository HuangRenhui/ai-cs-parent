package com.ai.cs.common.llm;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型调用用量事件：写入 Redis 流，由 ai-cs-job 消费落库。
 *
 * @author ai-cs
 */
@Data
public class ModelUsageEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型ID */
    private Long modelId;

    /** 模型名称 */
    private String modelName;

    /** 能力类型 */
    private String modelType;

    /** 供应方 */
    private String provider;

    /** 会话ID（可选） */
    private String sessionId;

    /** 输入 token */
    private Integer promptTokens;

    /** 输出 token */
    private Integer completionTokens;

    /** 总 token */
    private Integer totalTokens;

    /** 耗时(毫秒) */
    private Long latencyMs;

    /** 本次调用成本快照(元，按发生时单价计算) */
    private java.math.BigDecimal cost;

    /** 是否成功 1/0 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 发生时间戳(毫秒) */
    private Long ts;
}
