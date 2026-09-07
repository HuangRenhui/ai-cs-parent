package com.ai.cs.base.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 模型注册配置实体（对应表 cs_ai_model）
 *
 * @author ai-cs
 */
@Data
@TableName("cs_ai_model")
public class AiModelConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 显示名称 */
    private String modelName;

    /** 供应方: ollama/dashscope/openai/deepseek/other */
    private String provider;

    /** 能力: LLM/EMBEDDING/RERANK/VISION/MULTIMODAL */
    private String modelType;

    /** 服务地址 */
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

    /** 故障切换优先级(越小越优先) */
    private Integer priority;

    /** 是否启用 1是 0否 */
    private Integer enabled;

    /** 当前生效(同能力仅一条) 1是 0否 */
    private Integer isActive;

    /** 健康状态 UNKNOWN/HEALTHY/DOWN */
    private String health;

    private String remark;
}
