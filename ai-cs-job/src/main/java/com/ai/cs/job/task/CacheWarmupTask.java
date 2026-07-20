package com.ai.cs.job.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热定时任务
 * 每天凌晨3点预热系统缓存
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class CacheWarmupTask {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${job.cache-warmup:true}")
    private boolean warmupEnabled;

    @Scheduled(cron = "0 0 3 * * ?")
    public void warmupCache() {
        if (!warmupEnabled) {
            log.debug("[定时任务] 缓存预热已禁用，跳过");
            return;
        }

        log.info("[定时任务] 开始缓存预热...");

        try {
            // 1. 预热FAQ热门问题缓存（按访问次数排序前100条）
            warmupHotFaqs();

            // 2. 预热系统配置缓存
            warmupSysConfig();

            // 3. 预热工单类型统计缓存
            warmupWorkOrderStats();

            log.info("[定时任务] 缓存预热完成");
        } catch (Exception e) {
            log.error("[定时任务] 缓存预热失败", e);
        }
    }

    /**
     * 预热热门FAQ缓存
     */
    private void warmupHotFaqs() {
        try {
            String sql = "SELECT id, question, answer, visit_count FROM cs_knowledge_faq " +
                    "WHERE del_flag = 0 AND status = 1 ORDER BY visit_count DESC LIMIT 100";
            List<Map<String, Object>> hotFaqs = jdbcTemplate.queryForList(sql);

            if (hotFaqs != null && !hotFaqs.isEmpty()) {
                for (Map<String, Object> faq : hotFaqs) {
                    String cacheKey = "faq:hot:" + faq.get("id");
                    redisTemplate.opsForValue().set(cacheKey, faq, 1, TimeUnit.HOURS);
                }
                log.info("[缓存预热] 热门FAQ缓存预热完成，共 {} 条", hotFaqs.size());
            }
        } catch (Exception e) {
            log.warn("[缓存预热] FAQ缓存预热失败", e);
        }
    }

    /**
     * 预热系统配置缓存
     */
    private void warmupSysConfig() {
        try {
            String sql = "SELECT config_key, config_value FROM cs_sys_config WHERE del_flag = 0";
            List<Map<String, Object>> configs = jdbcTemplate.queryForList(sql);

            if (configs != null && !configs.isEmpty()) {
                for (Map<String, Object> config : configs) {
                    String cacheKey = "sys:config:" + config.get("config_key");
                    redisTemplate.opsForValue().set(cacheKey, config.get("config_value"), 2, TimeUnit.HOURS);
                }
                log.info("[缓存预热] 系统配置缓存预热完成，共 {} 条", configs.size());
            }
        } catch (Exception e) {
            log.warn("[缓存预热] 系统配置缓存预热失败", e);
        }
    }

    /**
     * 预热工单统计缓存
     */
    private void warmupWorkOrderStats() {
        try {
            // 按状态统计
            String statusSql = "SELECT order_status, COUNT(*) AS cnt FROM cs_work_order " +
                    "WHERE del_flag = 0 GROUP BY order_status";
            List<Map<String, Object>> statusStats = jdbcTemplate.queryForList(statusSql);
            redisTemplate.opsForValue().set("stats:workorder:status", statusStats, 30, TimeUnit.MINUTES);

            // 按类型统计
            String typeSql = "SELECT order_type, COUNT(*) AS cnt FROM cs_work_order " +
                    "WHERE del_flag = 0 AND order_type IS NOT NULL GROUP BY order_type";
            List<Map<String, Object>> typeStats = jdbcTemplate.queryForList(typeSql);
            redisTemplate.opsForValue().set("stats:workorder:type", typeStats, 30, TimeUnit.MINUTES);

            log.info("[缓存预热] 工单统计缓存预热完成");
        } catch (Exception e) {
            log.warn("[缓存预热] 工单统计缓存预热失败", e);
        }
    }
}
