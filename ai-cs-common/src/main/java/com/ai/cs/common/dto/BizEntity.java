package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 业务实体描述：Widget/对话上下文中携带的业务对象（订单、账户等），
 * 用于场景化开场白与工具调用时的实体定位。
 *
 * @author ai-cs
 */
@Data
public class BizEntity {
    /** 接入方自定义：order / account / appointment / policy 等 */
    private String type;
    /** 业务对象ID（如订单号），含义由 type 决定 */
    private String id;
}
