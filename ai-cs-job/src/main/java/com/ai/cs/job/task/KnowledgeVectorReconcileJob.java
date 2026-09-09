package com.ai.cs.job.task;

import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.job.support.JobLockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 知识库向量一致性对账任务
 * 定时检测"已发布且启用"但缺少向量的 FAQ，并调用 knowledge 服务补录向量
 */
@Slf4j
@Component
public class KnowledgeVectorReconcileJob {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private JobLockService jobLockService;
    @Resource
    private KnowledgeFeign knowledgeFeign;

    private static final String LOCK_KEY = "job:lock:knowledge-vector-reconcile";

    /**
     * 每 10 分钟执行一次向量对账
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void reconcile() {
        if (!jobLockService.tryLock(LOCK_KEY, Duration.ofMinutes(9))) {
            return;
        }
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM cs_knowledge_faq WHERE status = 1 AND audit_status = 2 AND del_flag = 0 "
                            + "AND (milvus_id IS NULL OR milvus_id = '')",
                    Long.class);
            if (ids.isEmpty()) {
                log.info("[向量对账] 无缺失向量的 FAQ，跳过");
                return;
            }
            log.info("[向量对账] 发现 {} 条缺失向量的 FAQ，开始补录", ids.size());
            int success = 0;
            int fail = 0;
            for (Long id : ids) {
                try {
                    knowledgeFeign.vectorizeById(id);
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[向量对账] 补录 FAQ id={} 失败: {}", id, e.getMessage());
                }
            }
            log.info("[向量对账] 完成：成功 {} 条，失败 {} 条", success, fail);
        } finally {
            jobLockService.unlock(LOCK_KEY);
        }
    }
}
