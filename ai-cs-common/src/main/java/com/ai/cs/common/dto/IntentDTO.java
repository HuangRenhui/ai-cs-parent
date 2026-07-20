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
    private String intent;
    private String entity;
}