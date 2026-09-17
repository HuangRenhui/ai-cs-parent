package com.ai.cs.knowledge.mapper;

import com.ai.cs.knowledge.entity.KnowledgeMiss;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识未命中记录 Mapper
 */
@Mapper
public interface KnowledgeMissMapper extends BaseMapper<KnowledgeMiss> {
}
