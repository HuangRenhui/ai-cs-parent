package com.ai.cs.knowledge.util;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MilvusHit {
    private long faqId;
    private float score;
    private String content;
}
