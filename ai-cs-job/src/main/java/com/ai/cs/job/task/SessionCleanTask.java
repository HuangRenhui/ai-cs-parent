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
 * 会话清理定时任务（占位）
 *
 * <p>TODO 后续实现：每天凌晨 2 点软删除（del_flag=1）已结束且结束时间超过保留天数的会话，
 * 并级联软删其聊天消息；进行中的长会话不受影响。</p>
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
     * 清理过期会话（占位）
     *
     * <p>TODO 后续实现：受开关与分布式锁控制；先按 {@code end_time < N 天前} 软删会话，
     * 再以「刚被软删的会话」为范围级联软删聊天消息，避免全量扫描历史已删会话。</p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredSessions() {
        log.info("[占位] 过期会话清理任务（未执行实际逻辑）cleanEnabled={} sessionExpireDays={}",
                cleanEnabled, sessionExpireDays);
    }
}
