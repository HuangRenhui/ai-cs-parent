package com.ai.cs.common.dto;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:45
 * @description 意图识别返回 DTO
 */
import lombok.Data;

@Data
public class IntentDTO {
    private String intent;
    private String entity;
}