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

/**
 * 知识未命中记录服务
 * 当RAG检索未命中任何FAQ时，记录用户问题及最高相似度分数，
 * 便于运营人员后续补充知识库（发现知识盲区）
 */
@Service
public class KnowledgeMissService extends ServiceImpl<KnowledgeMissMapper, KnowledgeMiss> {

    /**
     * 记录一次知识未命中事件
     * @param tenantCode 租户编码（为空时归一化为 default）
     * @param question 用户问题（超长截断到500字符，防止入库溢出）
     * @param sessionId 会话ID（便于追溯上下文）
     * @param topScore 本次检索的最高相似度分数（低于阈值才走到这里，可为空）
     */
    public void record(String tenantCode, String question, String sessionId, Float topScore) {
        KnowledgeMiss miss = new KnowledgeMiss();
        // 租户编码归一化，保证多租户统计口径一致
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

    /**
     * 分页查询未命中记录（按时间倒序，最新的盲区优先展示）
     * @param tenantCode 租户编码（可选，为空查全部租户）
     * @param page 页码（从1开始，小于1自动纠正为1）
     * @param size 每页条数（限制在1~100之间）
     * @return 分页结果
     */
    public PageResult<KnowledgeMiss> pageList(String tenantCode, long page, long size) {
        // 分页参数防御性校正
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
