package com.ai.cs.common.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 租户级 Token/日配额服务
 * 补全功能清单 P1 半成品「租户级仍缺失」
 * 每个租户每日对话与工具调用有独立上限，防账单被刷
 *
 * @author huangrenhui
 */
@Slf4j
@Component
public class TenantQuotaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String QUOTA_KEY_PREFIX = "ai:tenant:quota:";
    private static final String QUOTA_CONFIG_KEY = "ai:tenant:quota:config:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 检查租户是否超过当日配额
     *
     * @param tenantCode 租户编码
     * @return true=已超限，应拒绝或降级
     */
    public boolean isQuotaExceeded(String tenantCode) {
        if (redisTemplate == null || tenantCode == null || tenantCode.isBlank()) {
            return false;
        }
        try {
            String configKey = QUOTA_CONFIG_KEY + tenantCode;
            String dailyLimitStr = redisTemplate.opsForValue().get(configKey);
            long dailyLimit = parseLimit(dailyLimitStr);
            if (dailyLimit <= 0) {
                return false;
            }
            String usageKey = QUOTA_KEY_PREFIX + tenantCode + ":" + LocalDate.now().format(DAY);
            String currentStr = redisTemplate.opsForValue().get(usageKey);
            long current = currentStr != null ? Long.parseLong(currentStr) : 0;
            return current >= dailyLimit;
        } catch (Exception e) {
            log.warn("租户配额检查失败 tenant={}: {}", tenantCode, e.getMessage());
            return false;
        }
    }

    /**
     * 记录租户一次Token消耗
     *
     * @param tenantCode 租户编码
     * @param tokens     消耗的token数
     */
    public void recordUsage(String tenantCode, long tokens) {
        if (redisTemplate == null || tenantCode == null || tenantCode.isBlank() || tokens <= 0) {
            return;
        }
        try {
            String usageKey = QUOTA_KEY_PREFIX + tenantCode + ":" + LocalDate.now().format(DAY);
            redisTemplate.opsForValue().increment(usageKey, tokens);
            redisTemplate.expire(usageKey, 50, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("记录租户Token消耗失败 tenant={}: {}", tenantCode, e.getMessage());
        }
    }

    /**
     * 设置租户每日Token配额
     *
     * @param tenantCode 租户编码
     * @param dailyLimit 每日token上限（0=不限）
     */
    public void setQuota(String tenantCode, long dailyLimit) {
        if (redisTemplate == null || tenantCode == null || tenantCode.isBlank()) {
            return;
        }
        try {
            String configKey = QUOTA_CONFIG_KEY + tenantCode;
            redisTemplate.opsForValue().set(configKey, String.valueOf(dailyLimit));
        } catch (Exception e) {
            log.warn("设置租户配额失败 tenant={}: {}", tenantCode, e.getMessage());
        }
    }

    /**
     * 获取租户当前已用Token数
     */
    public long getCurrentUsage(String tenantCode) {
        if (redisTemplate == null || tenantCode == null || tenantCode.isBlank()) {
            return 0;
        }
        try {
            String usageKey = QUOTA_KEY_PREFIX + tenantCode + ":" + LocalDate.now().format(DAY);
            String currentStr = redisTemplate.opsForValue().get(usageKey);
            return currentStr != null ? Long.parseLong(currentStr) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取租户配额上限
     */
    public long getQuotaLimit(String tenantCode) {
        if (redisTemplate == null || tenantCode == null || tenantCode.isBlank()) {
            return 0;
        }
        try {
            String configKey = QUOTA_CONFIG_KEY + tenantCode;
            String limitStr = redisTemplate.opsForValue().get(configKey);
            return parseLimit(limitStr);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLimit(String limitStr) {
        if (limitStr == null || limitStr.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(limitStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}