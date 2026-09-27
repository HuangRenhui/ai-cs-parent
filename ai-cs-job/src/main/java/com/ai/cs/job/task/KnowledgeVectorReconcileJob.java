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
     * 向量对账（骨架：只列步骤，逻辑待实现）
     *
     * <p>目标：把「数据库里的知识」与「向量库里的向量」对齐，两个方向都要做——
     * 缺向量的补录、删不掉的补偿删除。每 10 分钟执行一次，多实例下必须互斥。</p>
     *
     * <p>实现要点（按顺序）：</p>
     * <ol>
     *   <li><b>取锁</b>：{@code jobLockService} 上锁失败就直接 return（另一实例正在跑），
     *       释放锁必须放在 {@code finally}；</li>
     *   <li><b>补偿删除</b>：消费 Redis Set {@code kb:vector:pending-delete}
     *       （成员格式 {@code 业务类型:归一化租户:业务id}，由 knowledge 侧删除失败时登记）；</li>
     *   <li><b>补录缺失向量</b>：查「启用 + 已发布」的 FAQ，与向量库比对差集，逐条调 knowledge 补录；</li>
     *   <li><b>统计与日志</b>：四个计数（补录成功/失败、补偿删除成功/失败）必须打日志，
     *       否则脏向量会静默累积；</li>
     *   <li><b>释放锁</b>。</li>
     * </ol>
     *
     * <p>本例只写注释不写实现；下面每步里的「坑」建议先读完再动手。</p>
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void reconcile() {
        // ===== 步骤 1：取分布式锁 =====
        // TODO jobLockService 的上锁/释放 API 先看一眼签名（tryLock 返回标识 or 布尔），
        //  多实例部署时这是唯一防重复执行的屏障；未取到锁直接 return，不要往下走。

        // ===== 步骤 2：补偿删除 Vector 待清理集合 =====
        // TODO 2.1 从 Redis Set POP 一批（建议 200 条/轮，避免单次跑太久）：
        //      StringRedisTemplate.opsForSet().pop(PENDING_DELETE_KEY, 200)
        //      —— 注意用 pop 而不是 members：取出即摘除，天然避免多实例重复处理同一批。
        // TODO 2.2 解析成员 "bizType:tenant:bizId"：用 split(":", 3)，
        //      因为归一化租户不含冒号（MilvusUtil.normalizeTenant 只保留 [a-z0-9_]），bizId 是数字。
        // TODO 2.3 按 bizType 分派删除：
        //      - faq  → MilvusUtil.deleteByFaqId(Long bizId, tenant)
        //      - chunk→ 目前 MilvusUtil 只有 deleteChunksByDocument，**缺**按 chunkId 删除的方法，
        //               需要先补一个 deleteChunkById(tenant, chunkId)（或按 documentId 整篇重删）
        //      注意：job 是独立服务，**不能直接注入 knowledge 的 MilvusUtil**，
        //      要么在 KnowledgeFeign 加一个「删除向量」接口，要么由 knowledge 提供内部接口供 job 调用。
        // TODO 2.4 失败处理：删除失败要把成员 SADD 回 Set（不能丢），并给重试计数上限（如 5 次），
        //      超过上限转人工队列/告警，否则会永久循环。

        // ===== 步骤 3：补录缺失向量 =====
        // TODO 3.1 取租户清单：SELECT DISTINCT tenant_code FROM cs_knowledge_faq WHERE status=1 AND audit_status=2 AND del_flag=0
        //      （audit_status=2 表示已发布；字段含义见 KnowledgeFaq 注释）
        // TODO 3.2 按租户分批取 FAQ id（建议 limit 500，避免一次 IN 太多）：
        //      SELECT id FROM cs_knowledge_faq WHERE tenant_code=? AND status=1 AND audit_status=2 AND del_flag=0
        // TODO 3.3 比对差集：调 knowledge 的向量化状态接口拿到「已向量化」集合，再取差集。
        //      现状：KnowledgeFeign 只有 vectorizeById，**缺**状态查询方法，
        //      需要先补一个对应 POST /knowledge/vectorize/status 的 Feign 方法（body 传 id 列表）。
        // TODO 3.4 逐条补录：knowledgeFeign.vectorizeById(id)，单条失败只记 warn 并累加失败计数，
        //      不要把整轮任务打断（与 RagSearchService.batchVectorizeAndInsert 的"逐条容错"口径保持一致）。
        // TODO 3.5 空租户/空集合直接跳过：向量集合不存在时状态查询返回空集，差集=全量，
        //      此时全量补录是**期望行为**（首次建库），但要注意限流，别把 embed 配额打满。

        // ===== 步骤 4：统计与可观测 =====
        // TODO 4.1 日志至少输出：轮次耗时、补录成功/失败、补偿删除成功/失败；
        // TODO 4.2 失败数超阈值（如 50）时按项目现有告警方式暴露（目前只有日志，可先 log.error）；
        // TODO 4.3 建议把本轮计数写入 Redis（如 kb:vector:reconcile:last），便于运维页展示。

        // ===== 步骤 5：释放锁 =====
        // TODO 放在 finally 中；若 jobLockService 需要校验持有者，务必用"上锁时拿到的标识"释放，
        //      避免误释放别人的锁。

        log.info("[占位] 知识库向量对账任务：已打印骨架步骤，未执行实际逻辑");
    }
}
