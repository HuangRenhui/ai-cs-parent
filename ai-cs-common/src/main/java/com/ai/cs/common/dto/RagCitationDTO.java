package com.ai.cs.common.dto;

import lombok.Data;

@Data
public class RagCitationDTO {
    private Long faqId;
    private String question;
    private String category;
    private Float score;
}
