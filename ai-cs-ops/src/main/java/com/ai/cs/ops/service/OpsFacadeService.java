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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    public List<OpsHealthItemVO> health() {
        return healthProbeService.probeAll();
    }

    public Map<String, String> self() {
        return Map.of("status", "UP", "service", "ai-cs-ops");
    }

    public OpsLogPageVO logs(OpsLogQuery query) {
        return logQueryAdapter.search(query);
    }

    public List<OpsLogEntryVO> logsByRequestId(String requestId) {
        return logQueryAdapter.byRequestId(requestId);
    }

    public OpsTracePageVO traces(String traceId, String sessionId, Integer page, Integer size) {
        return traceQueryAdapter.list(traceId, sessionId, page, size);
    }

    public OpsTraceVO traceDetail(String traceId) {
        return traceQueryAdapter.detail(traceId);
    }

    public OpsAlertBundleVO alerts() {
        return opsAlertStore.snapshot(healthProbeService.probeAll());
    }

    public OpsAlertBundleVO saveAlerts(List<OpsAlertRuleVO> rules) {
        opsAlertStore.replaceRules(rules);
        return alerts();
    }
}
