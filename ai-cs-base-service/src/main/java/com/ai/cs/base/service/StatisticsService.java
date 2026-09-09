package com.ai.cs.base.service;

import com.ai.cs.base.entity.Statistics;
import com.ai.cs.base.mapper.StatisticsMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据统计与分析服务
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
public class StatisticsService extends ServiceImpl<StatisticsMapper, Statistics> {

    /**
     * 获取系统概览数据
     */
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String weekAgo = LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 今日数据（size 为命中天数，当天只有一行，等价于是否有数据）
        overview.put("todaySessions", baseMapper.countChatSessions(today, today).size());
        overview.put("todayOrders", baseMapper.countWorkOrders(today, today).size());
        overview.put("todayCustomers", baseMapper.countNewCustomers(today, today).size());

        // 本周数据
        List<Map<String, Object>> weekSessions = baseMapper.countChatSessions(weekAgo, today);
        List<Map<String, Object>> weekOrders = baseMapper.countWorkOrders(weekAgo, today);
        overview.put("weekSessions", weekSessions.stream().mapToLong(m -> (Long) m.get("count")).sum());
        overview.put("weekOrders", weekOrders.stream().mapToLong(m -> (Long) m.get("count")).sum());

        return overview;
    }

    /**
     * 获取聊天统计趋势数据
     */
    public List<Map<String, Object>> getChatTrend(String startDate, String endDate) {
        return baseMapper.countChatSessions(startDate, endDate);
    }

    /**
     * 获取工单统计
     */
    public Map<String, Object> getWorkOrderStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("byStatus", baseMapper.countWorkOrderByStatus());
        stats.put("byType", baseMapper.countWorkOrderByType());
        stats.put("agentRank", baseMapper.rankAgentByWorkOrders());
        return stats;
    }

    /**
     * 获取客户增长趋势
     */
    public List<Map<String, Object>> getCustomerTrend(String startDate, String endDate) {
        return baseMapper.countNewCustomers(startDate, endDate);
    }

    /**
     * 获取仪表盘汇总数据
     */
    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String monthAgo = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 概览
        dashboard.put("overview", getOverview());
        // 聊天趋势（近30天）
        dashboard.put("chatTrend", getChatTrend(monthAgo, today));
        // 工单统计
        dashboard.put("workOrderStats", getWorkOrderStats());
        // 客户趋势（近30天）
        dashboard.put("customerTrend", getCustomerTrend(monthAgo, today));

        return dashboard;
    }
}
