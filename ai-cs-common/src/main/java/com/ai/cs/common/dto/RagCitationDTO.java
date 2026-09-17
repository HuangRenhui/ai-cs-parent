package com.ai.cs.common.dto;

import lombok.Data;

/**
 * RAG 引用来源：知识库命中后随回复返回，前端可展示"参考资料"列表
 *
 * @author ai-cs
 */
@Data
public class RagCitationDTO {
    /** 命中的 FAQ 主键 */
    private Long faqId;
    /** FAQ 标准问题 */
    private String question;
    /** FAQ 分类 */
    private String category;
    /** 相似度得分（0~1，越大越相关） */
    private Float score;
}
