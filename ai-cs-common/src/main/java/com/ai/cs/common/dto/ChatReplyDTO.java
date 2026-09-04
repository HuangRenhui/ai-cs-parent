package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatReplyDTO {
    private String sessionId;
    private String reply;
    private String intent;
    private String entity;
    private boolean transferred;
    /** HIT / MISS / UNAVAILABLE，仅咨询意图有值 */
    private String knowledgeStatus;
    private List<RagCitationDTO> citations = new ArrayList<>();
}
