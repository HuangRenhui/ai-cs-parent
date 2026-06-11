package com.ai.cs.knowledge.service;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:08
 * @description TODO
 */

import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.mapper.KnowledgeFaqMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class KnowledgeFaqService extends ServiceImpl<KnowledgeFaqMapper, KnowledgeFaq> {

    public List<KnowledgeFaq> getEnableFaqList() {
        LambdaQueryWrapper<KnowledgeFaq> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeFaq::getStatus, 1);
        return this.list(wrapper);
    }
}