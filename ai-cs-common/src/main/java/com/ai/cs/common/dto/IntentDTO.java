package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 意图识别返回 DTO
 *
 * @author huangrenhui
 * @date 2026/6/11 17:45
 */
@Data
public class IntentDTO {
    /** 意图名称（对应 IntentEnum 的中文名） */
    private String intent;
    /** 抽取的实体（单号/手机号等，无则为空串） */
    private String entity;
    /** 意图模型调用失败时为 true，调用方应返回繁忙话术而不是按咨询路由。 */
    private boolean llmDegraded;
}