package com.ai.cs.job.task;

import com.ai.cs.job.support.JobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据统计定时任务（占位）
 *
 * <p>TODO 后续实现：每小时汇总当前小时的新增数据，每日凌晨 0:05 汇总前一天完整数据。</p>
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class StatisticsTask {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private JobLockService jobLockService;

    /** 每小时统计开关 */
    @Value("${job.statistics-hourly:true}")
    private boolean hourlyEnabled;

    /** 每日统计开关 */
    @Value("${job.statistics-daily:true}")
    private boolean dailyEnabled;

    /**
     * 每小时统计（占位）
     *
     * <p>TODO 后续实现：统计「当前小时」新增的会话数、工单数、客户数，写入 cs_statistics
     * （stat_key 形如 {@code SESSION_HOURLY_09}）；cron 每小时整点触发。</p>
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyStatistics() {
        log.info("[占位] 每小时数据统计任务（未执行实际逻辑）hourlyEnabled={}", hourlyEnabled);
    }

    /**
     * 每日统计（占位）
     *
     * <p>TODO 后续实现：汇总前一天的会话总数、工单总数、已完成工单数、新增客户数、活跃坐席数，
     * 写入 cs_statistics；cron 每天 00:05 触发，避开 0 点整给跨天写入留落库时间。</p>
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void dailyStatistics() {
        log.info("[占位] 每日数据统计任务（未执行实际逻辑）dailyEnabled={}", dailyEnabled);
    }
}
