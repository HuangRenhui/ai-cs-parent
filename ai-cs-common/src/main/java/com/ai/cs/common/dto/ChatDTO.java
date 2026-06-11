package com.ai.cs.common.dto;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:45
 * @description 聊天 DTO
 */
import lombok.Data;

@Data
public class ChatDTO {
    private String sessionId;
    private String msg;
    private String history;
}
