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

    /**
     * 租户编码归一化：空值统一归为 "default" 租户，避免租户字段为null导致检索串租
     * @param tenantCode 原始租户编码
     * @return 归一化后的租户编码
     */
    public static String normalizeTenant(String tenantCode) {
        return StringUtils.hasText(tenantCode) ? tenantCode.trim() : "default";
    }

    /**
     * 获取全部启用状态的FAQ（不区分租户，兼容旧逻辑）
     * @return 启用FAQ列表
     */
    public List<KnowledgeFaq> getEnableFaqList() {
        return getEnableFaqList(null);
    }

    /**
     * 获取指定租户下启用状态的FAQ列表
     * @param tenantCode 租户编码（为空时查全部，不做租户过滤）
     * @return 按排序号升序、更新时间倒序排列的FAQ列表
     */
    public List<KnowledgeFaq> getEnableFaqList(String tenantCode) {
        LambdaQueryWrapper<KnowledgeFaq> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantCode)) {
            wrapper.eq(KnowledgeFaq::getTenantCode, normalizeTenant(tenantCode));
        }
        // status=1 表示启用
        wrapper.eq(KnowledgeFaq::getStatus, 1);
        wrapper.orderByAsc(KnowledgeFaq::getSortNum).orderByDesc(KnowledgeFaq::getUpdateTime);
        return this.list(wrapper);
    }

    /**
     * 分页查询FAQ列表，支持租户、状态、关键词组合过滤
     * @param tenantCode 租户编码（可选）
     * @param keyword 关键词（模糊匹配问题/答案/分类，可选）
     * @param status 状态（可选）
     * @param page 页码（从1开始，小于1自动纠正为1）
     * @param size 每页条数（限制在1~100之间）
     * @return 分页结果
     */
    public PageResult<KnowledgeFaq> pageList(String tenantCode, String keyword, Integer status, long page, long size) {
        // 分页参数防御性校正
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
            // 关键词同时匹配问题、答案、分类三个字段（OR关系）
            wrapper.and(w -> w.like(KnowledgeFaq::getQuestion, keyword)
                    .or().like(KnowledgeFaq::getAnswer, keyword)
                    .or().like(KnowledgeFaq::getCategory, keyword));
        }
        wrapper.orderByAsc(KnowledgeFaq::getSortNum).orderByDesc(KnowledgeFaq::getUpdateTime);
        Page<KnowledgeFaq> mp = this.page(new Page<>(p, s), wrapper);
        return PageResult.of(mp.getRecords(), mp.getTotal(), p, s);
    }
}
