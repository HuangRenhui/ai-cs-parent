package com.ai.cs.common.dto;

import lombok.Data;

@Data
public class BizEntity {
    /** 接入方自定义：order / account / appointment / policy 等 */
    private String type;
    private String id;
}
