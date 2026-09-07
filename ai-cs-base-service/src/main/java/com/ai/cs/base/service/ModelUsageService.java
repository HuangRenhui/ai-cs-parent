package com.ai.cs.base.service;

import com.ai.cs.base.entity.ModelUsageRecord;
import com.ai.cs.base.mapper.ModelUsageRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型用量记录查询服务
 *
 * @author ai-cs
 */
@Service
public class ModelUsageService extends ServiceImpl<ModelUsageRecordMapper, ModelUsageRecord> {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询用量/失败记录
     */
    public Page<ModelUsageRecord> page(int page, int size, Long modelId, String modelType,
                                       Integer success, String startTime, String endTime) {
        LambdaQueryWrapper<ModelUsageRecord> wrapper = new LambdaQueryWrapper<>();
        if (modelId != null) {
            wrapper.eq(ModelUsageRecord::getModelId, modelId);
        }
        if (StringUtils.hasText(modelType)) {
            wrapper.eq(ModelUsageRecord::getModelType, modelType);
        }
        if (success != null) {
            wrapper.eq(ModelUsageRecord::getSuccess, success);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(ModelUsageRecord::getCreateTime, LocalDateTime.parse(startTime, FMT));
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(ModelUsageRecord::getCreateTime, LocalDateTime.parse(endTime, FMT));
        }
        wrapper.orderByDesc(ModelUsageRecord::getId);
        return this.page(new Page<>(page, size), wrapper);
    }

    /**
     * 汇总统计：按模型分组的总调用、成功、失败、token、平均耗时
     */
    public Map<String, Object> summary(String modelType, String startTime, String endTime) {
        LambdaQueryWrapper<ModelUsageRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(modelType)) {
            wrapper.eq(ModelUsageRecord::getModelType, modelType);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(ModelUsageRecord::getCreateTime, LocalDateTime.parse(startTime, FMT));
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(ModelUsageRecord::getCreateTime, LocalDateTime.parse(endTime, FMT));
        }
        List<ModelUsageRecord> list = this.list(wrapper);
        Map<String, Object> result = new HashMap<>();
        long total = list.size();
        long success = list.stream().filter(r -> r.getSuccess() != null && r.getSuccess() == 1).count();
        long fail = total - success;
        long totalTokens = list.stream().mapToLong(r -> r.getTotalTokens() == null ? 0 : r.getTotalTokens()).sum();
        long promptTokens = list.stream().mapToLong(r -> r.getPromptTokens() == null ? 0 : r.getPromptTokens()).sum();
        long completionTokens = list.stream().mapToLong(r -> r.getCompletionTokens() == null ? 0 : r.getCompletionTokens()).sum();
        double avgLatency = list.stream().mapToLong(r -> r.getLatencyMs() == null ? 0 : r.getLatencyMs()).average().orElse(0);
        result.put("total", total);
        result.put("success", success);
        result.put("fail", fail);
        result.put("totalTokens", totalTokens);
        result.put("promptTokens", promptTokens);
        result.put("completionTokens", completionTokens);
        result.put("avgLatencyMs", Math.round(avgLatency));
        java.math.BigDecimal totalCost = list.stream()
                .map(r -> r.getCost() == null ? java.math.BigDecimal.ZERO : r.getCost())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        result.put("totalCost", totalCost.setScale(4, java.math.RoundingMode.HALF_UP));
        return result;
    }

    /**
     * 近 N 分钟失败次数（供告警）
     */
    public long recentFailCount(int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return this.count(new LambdaQueryWrapper<ModelUsageRecord>()
                .eq(ModelUsageRecord::getSuccess, 0)
                .ge(ModelUsageRecord::getCreateTime, since));
    }
}
