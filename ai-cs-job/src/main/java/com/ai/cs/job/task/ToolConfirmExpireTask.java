package com.ai.cs.job.task;

import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.job.support.JobLockService;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 高危工具确认单过期任务。
 *
 * <p>确认单在读取时也会惰性判定过期，本任务负责把"已过期且没人再查"的记录批量落库为
 * EXPIRED，并推送 WS 事件让前端卡片与待确认列表及时收敛。</p>
 *
 * <p>并发安全：更新时带 {@code AND status='CONFIRMING'} 条件，与人工确认/拒绝并发时
 * 只有一方能改成功，不会把已确认的单子覆盖成过期。</p>
 *
 * @author huangrenhui
 * @date 2026-11-25
 */
@Slf4j
@Component
public class ToolConfirmExpireTask {

    private static final String LOCK_KEY = "job:lock:tool-confirm-expire";
    private static final String STATUS_CONFIRMING = "CONFIRMING";
    private static final String STATUS_EXPIRED = "EXPIRED";
    /** 单轮上限，防止异常数据导致任务长时间占用线程 */
    private static final int MAX_ROUNDS = 50;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private JobLockService jobLockService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 任务开关：默认开启，压测或排障时可临时关闭 */
    @Value("${job.tool-confirm-expire:true}")
    private boolean expireEnabled;

    /** 单批处理条数，避免一次性锁住过多行 */
    @Value("${job.tool-confirm-expire-batch:200}")
    private int batchSize;

    /**
     * 每分钟扫描一次过期确认单。
     */
    @Scheduled(cron = "${job.tool-confirm-expire-cron:0 */1 * * * ?}")
    public void expireConfirming() {
        if (!expireEnabled) {
            return;
        }
        if (!jobLockService.tryLock(LOCK_KEY, Duration.ofMinutes(2))) {
            log.info("确认单过期任务未抢到锁，跳过本轮");
            return;
        }
        try {
            int total = expireInBatches();
            if (total > 0) {
                log.info("确认单过期任务完成 expiredCount={}", total);
            }
        } catch (Exception e) {
            log.error("确认单过期任务执行失败", e);
        } finally {
            jobLockService.unlock(LOCK_KEY);
        }
    }

    /**
     * 分批把过期 CONFIRMING 置为 EXPIRED 并推送。
     *
     * @return 实际更新成功的条数
     */
    private int expireInBatches() {
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, session_id FROM cs_open_tool_invoke "
                            + "WHERE status = ? AND expire_at IS NOT NULL AND expire_at < NOW() "
                            + "ORDER BY expire_at ASC LIMIT ?",
                    STATUS_CONFIRMING, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            int updated = 0;
            for (Map<String, Object> row : rows) {
                Long id = row.get("id") == null ? null : ((Number) row.get("id")).longValue();
                if (id == null) {
                    continue;
                }
                // 带状态条件：与人工确认并发时只有一方成功
                int affected = jdbcTemplate.update(
                        "UPDATE cs_open_tool_invoke SET status = ? WHERE id = ? AND status = ?",
                        STATUS_EXPIRED, id, STATUS_CONFIRMING);
                if (affected != 1) {
                    continue;
                }
                updated++;
                pushExpired(toText(row.get("session_id")), id);
            }
            total += updated;
            // 整批都被别人改走（如刚被人工确认）：继续查会重复命中同一批，直接结束
            if (updated == 0) {
                break;
            }
        }
        return total;
    }

    /** 推送 tool-confirm-expired；失败只记 warn，状态已落库不回头 */
    private void pushExpired(String sessionId, Long confirmId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "tool-confirm-expired");
            payload.put("confirmId", confirmId);
            payload.put("status", STATUS_EXPIRED);
            payload.put("content", "该操作已超时，如需继续请重新发起");
            stringRedisTemplate.convertAndSend(RedisKeyConst.WS_PUSH_CHANNEL,
                    sessionId.trim() + "\n" + payload.toJSONString());
        } catch (Exception e) {
            log.warn("确认单过期推送失败 confirmId={}", confirmId);
        }
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
