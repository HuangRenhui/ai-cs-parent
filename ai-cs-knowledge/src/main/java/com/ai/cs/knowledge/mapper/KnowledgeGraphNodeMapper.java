package com.ai.cs.knowledge.mapper;

import com.ai.cs.knowledge.entity.KnowledgeGraphNode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识图谱节点 Mapper
 */
@Mapper
public interface KnowledgeGraphNodeMapper extends BaseMapper<KnowledgeGraphNode> {
}
