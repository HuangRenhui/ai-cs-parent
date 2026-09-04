package com.ai.cs.knowledge.service;

import com.ai.cs.common.result.PageResult;
import com.ai.cs.knowledge.entity.KnowledgeFaq;
import com.ai.cs.knowledge.mapper.KnowledgeFaqMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * FAQ知识库服务
 *
 * @author huangrenhui
 * @date 2026/6/11 18:08
 */
@Service
public class KnowledgeFaqService extends ServiceImpl<KnowledgeFaqMapper, KnowledgeFaq> {

    public static String normalizeTenant(String tenantCode) {
        return StringUtils.hasText(tenantCode) ? tenantCode.trim() : "default";
    }

    public List<KnowledgeFaq> getEnableFaqList() {
        return getEnableFaqList(null);
    }

    public List<KnowledgeFaq> getEnableFaqList(String tenantCode) {
        LambdaQueryWrapper<KnowledgeFaq> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantCode)) {
            wrapper.eq(KnowledgeFaq::getTenantCode, normalizeTenant(tenantCode));
        }
        wrapper.eq(KnowledgeFaq::getStatus, 1);
        wrapper.orderByAsc(KnowledgeFaq::getSortNum).orderByDesc(KnowledgeFaq::getUpdateTime);
        return this.list(wrapper);
    }

    public PageResult<KnowledgeFaq> pageList(String tenantCode, String keyword, Integer status, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<KnowledgeFaq> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantCode)) {
            wrapper.eq(KnowledgeFaq::getTenantCode, normalizeTenant(tenantCode));
        }
        if (status != null) {
            wrapper.eq(KnowledgeFaq::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(KnowledgeFaq::getQuestion, keyword)
                    .or().like(KnowledgeFaq::getAnswer, keyword)
                    .or().like(KnowledgeFaq::getCategory, keyword));
        }
        wrapper.orderByAsc(KnowledgeFaq::getSortNum).orderByDesc(KnowledgeFaq::getUpdateTime);
        Page<KnowledgeFaq> mp = this.page(new Page<>(p, s), wrapper);
        return PageResult.of(mp.getRecords(), mp.getTotal(), p, s);
    }
}
