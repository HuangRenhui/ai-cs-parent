package com.ai.cs.ops.service;

import com.ai.cs.ops.dto.OpsAlertBundleVO;
import com.ai.cs.ops.dto.OpsAlertRuleVO;
import com.ai.cs.ops.dto.OpsHealthItemVO;
import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsLogPageVO;
import com.ai.cs.ops.dto.OpsLogQuery;
import com.ai.cs.ops.dto.OpsOverviewVO;
import com.ai.cs.ops.dto.OpsTracePageVO;
import com.ai.cs.ops.dto.OpsTraceVO;
import com.ai.cs.ops.query.FileLogQueryAdapter;
import com.ai.cs.ops.query.FileTraceQueryAdapter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 运营门面服务：聚合健康探测、日志/链路查询与告警计算，
 * 为运营控制器提供统一入口，并负责向告警存储注入模型失败指标数据源。
 */
@Service
public class OpsFacadeService {

    @Resource
    private FileLogQueryAdapter logQueryAdapter;
    @Resource
    private FileTraceQueryAdapter traceQueryAdapter;
    @Resource
    private HealthProbeService healthProbeService;
    @Resource
    private OpsAlertStore opsAlertStore;

    /** 基础服务地址：用于拉取模型调用失败统计，默认本地 8084 */
    @Value("${ops.base-service-url:http://localhost:8084}")
    private String baseServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 启动后把「模型近 5 分钟失败次数」查询回调注入告警存储，供告警规则实时取值。
     */
    @PostConstruct
    public void init() {
        opsAlertStore.setModelFailCountSupplier(this::queryModelFailCount);
    }

    /**
     * 从基础服务拉取近 5 分钟模型调用失败次数；查询失败时返回 0，不阻塞告警主流程。
     */
    private Long queryModelFailCount() {
        try {
            String url = baseServiceUrl + "/system/ai-model/usage/recent-fail?minutes=5";
            Map<?, ?> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && resp.get("data") instanceof Number) {
                return ((Number) resp.get("data")).longValue();
            }
        } catch (Exception e) {
            // 查询失败不阻塞告警主流程
        }
        return 0L;
    }

    /**
     * 运营总览：实时探测全部服务健康状态，叠加日志错误统计与告警快照。
     */
    public OpsOverviewVO overview() {
        List<OpsHealthItemVO> health = healthProbeService.probeAll();
        long up = health.stream().filter(item -> "UP".equalsIgnoreCase(item.getStatus())).count();
        OpsAlertBundleVO alerts = opsAlertStore.snapshot(health);
        OpsOverviewVO vo = new OpsOverviewVO();
        vo.setAdapter(logQueryAdapter.source());
        vo.setHint("近 1h 错误率/延迟需接指标后端。当前展示健康探测、本地日志错误条数与临时告警。");
        vo.setServicesTotal(health.size());
        vo.setServicesUp((int) up);
        vo.setServicesDown(health.size() - (int) up);
        vo.setLogFiles(logQueryAdapter.fileCount());
        vo.setRecentErrorCount(logQueryAdapter.recentErrorCount());
        vo.setPendingAlerts(alerts.getEvents().size());
        return vo;
    }

    /**
     * 实时健康探测全部服务。
     */
    public List<OpsHealthItemVO> health() {
        return healthProbeService.probeAll();
    }

    /**
     * 本服务自检信息（供其他服务探测本服务时返回）。
     */
    public Map<String, String> self() {
        return Map.of("status", "UP", "service", "ai-cs-ops");
    }

    /**
     * 多条件分页搜索日志。
     */
    public OpsLogPageVO logs(OpsLogQuery query) {
        return logQueryAdapter.search(query);
    }

    /**
     * 按请求 ID 串起全链路日志。
     */
    public List<OpsLogEntryVO> logsByRequestId(String requestId) {
        return logQueryAdapter.byRequestId(requestId);
    }

    /**
     * 分页查询链路（按 traceId/sessionId 过滤）。
     */
    public OpsTracePageVO traces(String traceId, String sessionId, Integer page, Integer size) {
        return traceQueryAdapter.list(traceId, sessionId, page, size);
    }

    /**
     * 查询单条链路详情。
     */
    public OpsTraceVO traceDetail(String traceId) {
        return traceQueryAdapter.detail(traceId);
    }

    /**
     * 查询告警快照：先实时探测健康状态，再计算触发中的告警事件。
     */
    public OpsAlertBundleVO alerts() {
        return opsAlertStore.snapshot(healthProbeService.probeAll());
    }

    /**
     * 保存告警规则（启停/通道），返回最新快照。
     */
    public OpsAlertBundleVO saveAlerts(List<OpsAlertRuleVO> rules) {
        opsAlertStore.replaceRules(rules);
        return alerts();
    }
}
