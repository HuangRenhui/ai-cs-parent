package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库检索/问答结果：知识服务返回给 Agent 的内部结果对象。
 * 三态状态让调用方能区分"没查到"(MISS) 与"服务挂了"(UNAVAILABLE)，走不同降级话术。
 *
 * @author ai-cs
 */
@Data
public class RagSearchResultDTO {
    /** 命中 */
    public static final String HIT = "HIT";
    /** 未命中（知识库无相关资料） */
    public static final String MISS = "MISS";
    /** 知识服务不可用（模型/向量服务故障） */
    public static final String UNAVAILABLE = "UNAVAILABLE";

    /** HIT / MISS / UNAVAILABLE */
    private String status;
    /** 回复文本（HIT/MISS 时有值） */
    private String reply;
    /** 引用来源列表（HIT 时有值） */
    private List<RagCitationDTO> citations = new ArrayList<>();
    /** 错误信息（UNAVAILABLE 时有值） */
    private String error;

    /** 构造命中结果；citations 传 null 兜底为空列表 */
    public static RagSearchResultDTO hit(String reply, List<RagCitationDTO> citations) {
        RagSearchResultDTO dto = new RagSearchResultDTO();
        dto.setStatus(HIT);
        dto.setReply(reply);
        dto.setCitations(citations == null ? new ArrayList<>() : citations);
        return dto;
    }

    /** 构造未命中结果（reply 为兜底话术） */
    public static RagSearchResultDTO miss(String reply) {
        RagSearchResultDTO dto = new RagSearchResultDTO();
        dto.setStatus(MISS);
        dto.setReply(reply);
        return dto;
    }

    /** 构造服务不可用结果（error 记录原因，仅供日志/排查） */
    public static RagSearchResultDTO unavailable(String error) {
        RagSearchResultDTO dto = new RagSearchResultDTO();
        dto.setStatus(UNAVAILABLE);
        dto.setError(error);
        return dto;
    }
}
