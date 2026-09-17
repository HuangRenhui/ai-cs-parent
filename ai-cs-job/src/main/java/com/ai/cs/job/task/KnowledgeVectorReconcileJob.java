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
 * 知识库向量一致性对账任务（占位）
 *
 * <p>TODO 后续实现：定时检测「已发布且启用」但缺少向量的 FAQ，调用 knowledge 服务补录向量。</p>
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
     * 向量对账（占位）
     *
     * <p>TODO 后续实现：取分布式锁后查出 {@code milvus_id} 为空的已发布 FAQ，
     * 逐条调用 knowledge 的向量化接口补录，并统计成功/失败条数；每 10 分钟执行一次。</p>
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void reconcile() {
        log.info("[占位] 知识库向量对账任务（未执行实际逻辑）");
    }
}
