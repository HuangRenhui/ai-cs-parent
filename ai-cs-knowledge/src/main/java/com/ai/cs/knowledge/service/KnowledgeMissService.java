package com.ai.cs.knowledge.service;

import com.ai.cs.common.result.PageResult;
import com.ai.cs.knowledge.entity.KnowledgeMiss;
import com.ai.cs.knowledge.mapper.KnowledgeMissMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class KnowledgeMissService extends ServiceImpl<KnowledgeMissMapper, KnowledgeMiss> {

    public void record(String tenantCode, String question, String sessionId, Float topScore) {
        KnowledgeMiss miss = new KnowledgeMiss();
        miss.setTenantCode(KnowledgeFaqService.normalizeTenant(tenantCode));
        miss.setQuestion(question == null ? "" : question.trim());
        if (miss.getQuestion().length() > 500) {
            miss.setQuestion(miss.getQuestion().substring(0, 500));
        }
        miss.setSessionId(sessionId);
        miss.setTopScore(topScore);
        miss.setCreateTime(LocalDateTime.now());
        this.save(miss);
    }

    public PageResult<KnowledgeMiss> pageList(String tenantCode, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<KnowledgeMiss> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantCode)) {
            wrapper.eq(KnowledgeMiss::getTenantCode, KnowledgeFaqService.normalizeTenant(tenantCode));
        }
        wrapper.orderByDesc(KnowledgeMiss::getCreateTime);
        Page<KnowledgeMiss> mp = this.page(new Page<>(p, s), wrapper);
        return PageResult.of(mp.getRecords(), mp.getTotal(), p, s);
    }
}
