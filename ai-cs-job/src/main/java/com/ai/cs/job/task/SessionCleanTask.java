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

    @Value("${job.session-expire-days:7}")
    private int sessionExpireDays;

    @Value("${job.clean-expired-session:true}")
    private boolean cleanEnabled;

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

            // 清理过期会话中的聊天消息
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
