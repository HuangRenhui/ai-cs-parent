package com.ai.cs.base.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 模型调用用量记录实体（对应表 cs_model_usage）
 *
 * @author ai-cs
 */
@Data
@TableName("cs_model_usage")
public class ModelUsageRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型ID */
    private Long modelId;

    /** 模型名称 */
    private String modelName;

    /** 能力类型 */
    private String modelType;

    /** 供应方 */
    private String provider;

    /** 会话ID */
    private String sessionId;

    /** 输入 token */
    private Integer promptTokens;

    /** 输出 token */
    private Integer completionTokens;

    /** 总 token */
    private Integer totalTokens;

    /** 耗时(毫秒) */
    private Long latencyMs;

    /** 本次调用成本快照(元) */
    private java.math.BigDecimal cost;

    /** 是否成功 1/0 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;
}
