package com.ai.cs.knowledge.mapper;

import com.ai.cs.knowledge.entity.MultimodalKnowledge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 多模态知识条目 Mapper
 */
@Mapper
public interface MultimodalKnowledgeMapper extends BaseMapper<MultimodalKnowledge> {
}
