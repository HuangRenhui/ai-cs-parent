package com.ai.cs.job.task;

import com.ai.cs.job.support.JobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 会话清理定时任务
 * 每天凌晨2点清理过期会话
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class SessionCleanTask {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private JobLockService jobLockService;

    /** 会话保留天数：结束时间超过该天数的会话将被清理（默认 7 天） */
    @Value("${job.session-expire-days:7}")
    private int sessionExpireDays;

    /** 清理开关 */
    @Value("${job.clean-expired-session:true}")
    private boolean cleanEnabled;

    /**
     * 清理过期会话（cron：每天凌晨 2 点执行，避开业务高峰）
     * 采用软删除（del_flag=1）而非物理删除，保留数据可追溯；
     * 仅清理"已结束"的会话，进行中的长会话不受影响
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredSessions() {
        if (!cleanEnabled) {
            log.debug("[定时任务] 会话清理已禁用，跳过");
            return;
        }
        if (!jobLockService.tryLock("job:lock:session-clean", Duration.ofMinutes(30))) {
            log.info("[定时任务] 会话清理未拿到锁，跳过");
            return;
        }
        log.info("[定时任务] 开始清理过期会话...");

        try {
            // 计算过期时间点（N天前）
            String expireTime = LocalDateTime.now()
                    .minusDays(sessionExpireDays)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 只清理已结束且结束时间超 N 天的会话，不碰进行中的长会话
            String cleanSql = "UPDATE cs_chat_session SET del_flag = 1, update_time = NOW() " +
                    "WHERE del_flag = 0 AND end_time IS NOT NULL AND end_time < ?";
            int cleanedCount = jdbcTemplate.update(cleanSql, expireTime);

            // 清理过期会话中的聊天消息：以上一步"刚被软删"的会话（1 小时内更新）为范围做级联软删，
            // 避免全量扫描历史已删会话
            String cleanMsgSql = "UPDATE cs_chat_msg SET del_flag = 1, update_time = NOW() " +
                    "WHERE del_flag = 0 AND session_id IN (" +
                    "  SELECT session_id FROM cs_chat_session WHERE del_flag = 1 AND update_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)" +
                    ")";
            int cleanedMsgCount = jdbcTemplate.update(cleanMsgSql);

            log.info("[定时任务] 过期会话清理完成 - 清理会话: {} 条, 清理消息: {} 条", cleanedCount, cleanedMsgCount);
        } catch (Exception e) {
            log.error("[定时任务] 过期会话清理失败", e);
        } finally {
            jobLockService.unlock("job:lock:session-clean");
        }
    }
}
