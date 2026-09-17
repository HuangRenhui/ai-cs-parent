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
import java.util.stream.Collectors;

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
        // status=1 表示启用，auditStatus=2 表示已发布（仅已发布且启用的 FAQ 参与检索）
        wrapper.eq(KnowledgeFaq::getStatus, 1);
        wrapper.eq(KnowledgeFaq::getAuditStatus, 2);
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

    /**
     * 点赞/点踩反馈：累加计数
     *
     * @param id FAQ ID
     * @param type like / dislike
     * @return 是否成功
     */
    public boolean feedback(Long id, String type) {
        KnowledgeFaq faq = this.getById(id);
        if (faq == null) {
            return false;
        }
        if ("like".equalsIgnoreCase(type)) {
            faq.setLikeCount((faq.getLikeCount() == null ? 0 : faq.getLikeCount()) + 1);
        } else if ("dislike".equalsIgnoreCase(type)) {
            faq.setDislikeCount((faq.getDislikeCount() == null ? 0 : faq.getDislikeCount()) + 1);
        } else {
            return false;
        }
        return this.updateById(faq);
    }

    /**
     * 浏览量 +1
     *
     * @param id FAQ ID
     * @return 是否成功
     */
    public boolean view(Long id) {
        KnowledgeFaq faq = this.getById(id);
        if (faq == null) {
            return false;
        }
        faq.setViewCount((faq.getViewCount() == null ? 0 : faq.getViewCount()) + 1);
        return this.updateById(faq);
    }

    /**
     * 分页查询已发布 FAQ（供对外帮助中心使用，仅返回已发布且启用的，按热度排序）
     */
    public PageResult<KnowledgeFaq> pagePublished(String tenantCode, String category, String keyword, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<KnowledgeFaq> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantCode)) {
            wrapper.eq(KnowledgeFaq::getTenantCode, normalizeTenant(tenantCode));
        }
        wrapper.eq(KnowledgeFaq::getStatus, 1);
        wrapper.eq(KnowledgeFaq::getAuditStatus, 2);
        if (StringUtils.hasText(category)) {
            wrapper.eq(KnowledgeFaq::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(KnowledgeFaq::getQuestion, keyword).or().like(KnowledgeFaq::getAnswer, keyword));
        }
        wrapper.orderByDesc(KnowledgeFaq::getLikeCount).orderByDesc(KnowledgeFaq::getViewCount).orderByAsc(KnowledgeFaq::getSortNum);
        Page<KnowledgeFaq> mp = this.page(new Page<>(p, s), wrapper);
        return PageResult.of(mp.getRecords(), mp.getTotal(), p, s);
    }

    /**
     * 查询已发布 FAQ 的分类列表（去重）
     */
    public List<String> listCategories(String tenantCode) {
        List<KnowledgeFaq> faqs = getEnableFaqList(tenantCode);
        return faqs.stream()
                .map(KnowledgeFaq::getCategory)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
