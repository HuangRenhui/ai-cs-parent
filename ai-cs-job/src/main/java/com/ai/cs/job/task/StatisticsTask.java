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
 * 数据统计定时任务
 * 每小时汇总统计数据，每日生成数据报表
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
     * 每小时统计：汇总当前小时的新增数据
     * cron 含义：每小时整点（0 分 0 秒）触发，统计的是"当前小时"开头至今的数据（分钟级近似）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyStatistics() {
        if (!hourlyEnabled) {
            log.debug("[定时任务] 每小时统计已禁用，跳过");
            return;
        }
        if (!jobLockService.tryLock("job:lock:stats-hourly", Duration.ofMinutes(10))) {
            log.info("[定时任务] 每小时统计未拿到锁，跳过");
            return;
        }
        log.info("[定时任务] 开始执行每小时数据统计...");

        try {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String hourKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH"));

            // 统计本小时新增会话数
            String sessionSql = "SELECT COUNT(*) FROM cs_chat_session " +
                    "WHERE create_time >= CONCAT(?, ' ', ?, ':00:00') " +
                    "AND create_time < DATE_ADD(CONCAT(?, ' ', ?, ':00:00'), INTERVAL 1 HOUR) " +
                    "AND del_flag = 0";
            Integer sessionCount = jdbcTemplate.queryForObject(sessionSql, Integer.class, today, hourKey, today, hourKey);

            // 统计本小时新增工单数
            String orderSql = "SELECT COUNT(*) FROM cs_work_order " +
                    "WHERE create_time >= CONCAT(?, ' ', ?, ':00:00') " +
                    "AND create_time < DATE_ADD(CONCAT(?, ' ', ?, ':00:00'), INTERVAL 1 HOUR) " +
                    "AND del_flag = 0";
            Integer orderCount = jdbcTemplate.queryForObject(orderSql, Integer.class, today, hourKey, today, hourKey);

            // 统计本小时新增客户数
            String customerSql = "SELECT COUNT(*) FROM cs_customer " +
                    "WHERE create_time >= CONCAT(?, ' ', ?, ':00:00') " +
                    "AND create_time < DATE_ADD(CONCAT(?, ' ', ?, ':00:00'), INTERVAL 1 HOUR) " +
                    "AND del_flag = 0";
            Integer customerCount = jdbcTemplate.queryForObject(customerSql, Integer.class, today, hourKey, today, hourKey);

            // 保存或更新统计记录
            upsertStat(today, "SESSION_HOURLY_" + hourKey, "会话数(小时)", sessionCount != null ? sessionCount : 0);
            upsertStat(today, "ORDER_HOURLY_" + hourKey, "工单数(小时)", orderCount != null ? orderCount : 0);
            upsertStat(today, "CUSTOMER_HOURLY_" + hourKey, "客户数(小时)", customerCount != null ? customerCount : 0);

            log.info("[定时任务] 每小时数据统计完成 - 会话:{}, 工单:{}, 客户:{}", sessionCount, orderCount, customerCount);
        } catch (Exception e) {
            log.error("[定时任务] 每小时数据统计失败", e);
        } finally {
            jobLockService.unlock("job:lock:stats-hourly");
        }
    }

    /**
     * 每日统计：凌晨0:05汇总前一天完整数据
     * cron 含义：每天 00:05 触发；特意避开 0 点整，给跨天写入的数据留出落库时间
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void dailyStatistics() {
        if (!dailyEnabled) {
            log.debug("[定时任务] 每日统计已禁用，跳过");
            return;
        }
        if (!jobLockService.tryLock("job:lock:stats-daily", Duration.ofMinutes(20))) {
            log.info("[定时任务] 每日统计未拿到锁，跳过");
            return;
        }
        log.info("[定时任务] 开始执行每日数据统计...");

        try {
            String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

            // 统计昨日会话总数
            String sessionSql = "SELECT COUNT(*) FROM cs_chat_session " +
                    "WHERE DATE(create_time) = ? AND del_flag = 0";
            Integer sessionCount = jdbcTemplate.queryForObject(sessionSql, Integer.class, yesterday);

            // 统计昨日工单总数
            String orderSql = "SELECT COUNT(*) FROM cs_work_order " +
                    "WHERE DATE(create_time) = ? AND del_flag = 0";
            Integer orderCount = jdbcTemplate.queryForObject(orderSql, Integer.class, yesterday);

            // 统计昨日已完成工单数
            String completedSql = "SELECT COUNT(*) FROM cs_work_order " +
                    "WHERE DATE(update_time) = ? AND order_status = 3 AND del_flag = 0";
            Integer completedCount = jdbcTemplate.queryForObject(completedSql, Integer.class, yesterday);

            // 统计昨日新增客户数
            String customerSql = "SELECT COUNT(*) FROM cs_customer " +
                    "WHERE DATE(create_time) = ? AND del_flag = 0";
            Integer customerCount = jdbcTemplate.queryForObject(customerSql, Integer.class, yesterday);

            // 统计昨日活跃坐席数
            String agentSql = "SELECT COUNT(DISTINCT agent_id) FROM cs_chat_session " +
                    "WHERE DATE(create_time) = ? AND agent_id IS NOT NULL AND agent_id > 0 AND del_flag = 0";
            Integer activeAgentCount = jdbcTemplate.queryForObject(agentSql, Integer.class, yesterday);

            // 保存每日统计
            upsertStat(yesterday, "DAILY_SESSION", "会话总数", sessionCount != null ? sessionCount : 0);
            upsertStat(yesterday, "DAILY_ORDER", "工单总数", orderCount != null ? orderCount : 0);
            upsertStat(yesterday, "DAILY_ORDER_COMPLETED", "已完成工单数", completedCount != null ? completedCount : 0);
            upsertStat(yesterday, "DAILY_CUSTOMER", "新增客户数", customerCount != null ? customerCount : 0);
            upsertStat(yesterday, "DAILY_ACTIVE_AGENT", "活跃坐席数", activeAgentCount != null ? activeAgentCount : 0);

            log.info("[定时任务] 每日数据统计完成 - 会话:{}, 工单:{}(完成:{}), 客户:{}, 坐席:{}",
                    sessionCount, orderCount, completedCount, customerCount, activeAgentCount);
        } catch (Exception e) {
            log.error("[定时任务] 每日数据统计失败", e);
        } finally {
            jobLockService.unlock("job:lock:stats-daily");
        }
    }

    /**
     * 插入或更新统计记录（使用INSERT ON DUPLICATE KEY UPDATE，依赖 stat_date+stat_key 唯一键，
     * 任务重跑或锁失效时也不会产生重复行，天然幂等）
     *
     * @param statDate 统计日期（yyyy-MM-dd）
     * @param statKey  统计指标键，如 DAILY_SESSION、SESSION_HOURLY_09
     * @param remark   指标中文说明
     * @param count    统计数量
     */
    private void upsertStat(String statDate, String statKey, String remark, long count) {
        try {
            String sql = "INSERT INTO cs_statistics (stat_date, stat_type, stat_key, stat_value, stat_count, remark, create_time) " +
                    "VALUES (?, 'DAILY', ?, ?, ?, ?, NOW()) " +
                    "ON DUPLICATE KEY UPDATE stat_count = VALUES(stat_count), remark = VALUES(remark)";
            jdbcTemplate.update(sql, statDate, statKey, BigDecimal.valueOf(count), count, remark);
        } catch (Exception e) {
            log.warn("保存统计记录失败: statDate={}, statKey={}", statDate, statKey, e);
        }
    }
}
