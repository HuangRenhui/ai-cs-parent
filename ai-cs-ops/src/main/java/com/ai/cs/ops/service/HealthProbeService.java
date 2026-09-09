package com.ai.cs.ops.service;

import com.ai.cs.ops.config.OpsProperties;
import com.ai.cs.ops.dto.OpsHealthItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务健康探测服务：对配置的服务清单逐个发起 HTTP GET，按响应码判定 UP/DOWN，
 * 结果供总览大盘与告警规则（service.down）使用。
 */
@Service
public class HealthProbeService {

    @Resource
    private OpsProperties opsProperties;

    /**
     * 探测配置清单中的全部服务，返回每项的探测结果。
     */
    public List<OpsHealthItemVO> probeAll() {
        List<OpsHealthItemVO> items = new ArrayList<>();
        for (OpsProperties.ServiceEndpoint endpoint : opsProperties.getServices()) {
            items.add(probe(endpoint.getName(), endpoint.getUrl()));
        }
        return items;
    }

    /**
     * 探测单个服务：2xx 判定 UP，其余响应码或异常均判定 DOWN；
     * 记录耗时与失败原因，异常信息只保留异常类名，避免泄露内部堆栈。
     */
    public OpsHealthItemVO probe(String name, String url) {
        OpsHealthItemVO item = new OpsHealthItemVO();
        item.setName(name);
        item.setUrl(url);
        // 未配置地址视为 DOWN，让配置缺失在大盘上可见
        if (!StringUtils.hasText(url)) {
            item.setStatus("DOWN");
            item.setMessage("未配置探测地址");
            return item;
        }
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(opsProperties.getConnectTimeoutMs());
            connection.setReadTimeout(opsProperties.getConnectTimeoutMs());
            // 禁止跟随重定向：探测目标是服务本身，跳转后结果不代表原服务状态
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            item.setHttpStatus(code);
            item.setLatencyMs(System.currentTimeMillis() - start);
            item.setStatus(code >= 200 && code < 300 ? "UP" : "DOWN");
            item.setMessage(code >= 200 && code < 300 ? "可达" : "HTTP " + code);
        } catch (Exception e) {
            item.setLatencyMs(System.currentTimeMillis() - start);
            item.setStatus("DOWN");
            item.setMessage(e.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return item;
    }
}
