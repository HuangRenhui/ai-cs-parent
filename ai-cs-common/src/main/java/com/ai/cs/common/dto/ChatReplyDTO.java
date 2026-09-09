package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天回复 DTO：AI 应答的统一出参
 *
 * @author ai-cs
 */
@Data
public class ChatReplyDTO {
    /** 会话ID（新会话时返回服务端生成的 ID，前端需带回后续请求） */
    private String sessionId;
    /** AI 回复文本 */
    private String reply;
    /** 识别出的意图（咨询/查物流/退款/投诉/转人工） */
    private String intent;
    /** 抽取到的实体（单号等） */
    private String entity;
    /** 是否已转人工 */
    private boolean transferred;
    /** HIT / MISS / UNAVAILABLE，仅咨询意图有值 */
    private String knowledgeStatus;
    /** 知识库引用来源（命中时返回，前端可展示"参考资料"） */
    private List<RagCitationDTO> citations = new ArrayList<>();
}
