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

@Service
public class HealthProbeService {

    @Resource
    private OpsProperties opsProperties;

    public List<OpsHealthItemVO> probeAll() {
        List<OpsHealthItemVO> items = new ArrayList<>();
        for (OpsProperties.ServiceEndpoint endpoint : opsProperties.getServices()) {
            items.add(probe(endpoint.getName(), endpoint.getUrl()));
        }
        return items;
    }

    public OpsHealthItemVO probe(String name, String url) {
        OpsHealthItemVO item = new OpsHealthItemVO();
        item.setName(name);
        item.setUrl(url);
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
