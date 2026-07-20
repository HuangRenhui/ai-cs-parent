package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.mapper.KnowledgeFaqMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FAQ知识库服务
 *
 * @author huangrenhui
 * @date 2026/6/11 18:08
 */
@Service
public class KnowledgeFaqService extends ServiceImpl<KnowledgeFaqMapper, KnowledgeFaq> {

    public List<KnowledgeFaq> getEnableFaqList() {
        LambdaQueryWrapper<KnowledgeFaq> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeFaq::getStatus, 1);
        return this.list(wrapper);
    }
}