package com.ai.cs.common.llm;

import com.ai.cs.common.exception.BusinessException;
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
     * @return true=已超限；未配置上限（limit&lt;=0）或 Redis 不可用时返回 false（放行）。
     *         租户编码为空属于调用方错误，按超限拒绝
     */
    public boolean isQuotaExceeded(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            log.warn("租户编码为空，按超限拒绝");
            return true;
        }
        if (redisTemplate == null) {
            // 配额是成本保护而非鉴权：Redis 未接入时放行，避免整条对话链路不可用
            log.warn("Redis 未接入，跳过租户配额检查 tenant={}", tenantCode);
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
            // 同理：Redis 抖动不能让所有租户都被判定超限，放行并留告警日志
            log.warn("租户配额检查失败，本次放行 tenant={}", tenantCode, e);
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
            Long current = redisTemplate.opsForValue().increment(usageKey, tokens);
            // 只在首次写入时设置 TTL：每次写都 expire 会不断续期，跨天也清不掉
            if (current != null && current == tokens) {
                redisTemplate.expire(usageKey, 50, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("记录租户Token消耗失败 tenant={}", tenantCode, e);
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
            throw new BusinessException("配额服务不可用");
        }
        try {
            String configKey = QUOTA_CONFIG_KEY + tenantCode;
            redisTemplate.opsForValue().set(configKey, String.valueOf(dailyLimit));
        } catch (Exception e) {
            log.warn("设置租户配额失败 tenant={}", tenantCode, e);
            throw new BusinessException("配额保存失败，请稍后重试");
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
            log.warn("读取租户用量失败 tenant={}", tenantCode, e);
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
            log.warn("读取租户配额上限失败 tenant={}", tenantCode, e);
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