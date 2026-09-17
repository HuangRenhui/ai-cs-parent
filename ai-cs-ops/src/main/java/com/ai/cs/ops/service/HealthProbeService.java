package com.ai.cs.ops.service;

import com.ai.cs.ops.config.OpsProperties;
import com.ai.cs.ops.dto.OpsHealthItemVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务健康探测服务（占位）。
 *
 * <p>TODO 后续实现：对配置清单中的每个服务发起 HTTP GET（禁止跟随重定向），
 * 以 2xx 判定 UP、其余响应码或异常判定 DOWN，并记录耗时与失败原因（异常只保留类名）。
 * 当前不对任何服务发起探测，状态统一为 {@code UNKNOWN}，因此「服务不可达」告警不会触发。</p>
 *
 * <p>注意：占位态不返回 UP，避免在未探测的情况下把服务显示为健康。</p>
 */
@Slf4j
@Service
public class HealthProbeService {

    /** 占位态的探测结果状态：未探测（前端据非 UP 视为不可用） */
    private static final String STATUS_UNKNOWN = "UNKNOWN";

    @Resource
    private OpsProperties opsProperties;

    /**
     * 探测配置清单中的全部服务（占位：按配置回显 UNKNOWN）。
     */
    public List<OpsHealthItemVO> probeAll() {
        log.info("[占位] 服务健康探测未实现，状态统一回显 UNKNOWN");
        List<OpsHealthItemVO> items = new ArrayList<>();
        for (OpsProperties.ServiceEndpoint endpoint : opsProperties.getServices()) {
            items.add(placeholder(endpoint.getName(), endpoint.getUrl()));
        }
        return items;
    }

    /**
     * 探测单个服务（占位：不发起请求，返回 UNKNOWN）。
     */
    public OpsHealthItemVO probe(String name, String url) {
        log.info("[占位] 单个服务健康探测未实现 name={} url={}", name, url);
        return placeholder(name, url);
    }

    /**
     * 组装未探测状态的返回项。
     */
    private OpsHealthItemVO placeholder(String name, String url) {
        OpsHealthItemVO item = new OpsHealthItemVO();
        item.setName(name);
        item.setUrl(url);
        item.setStatus(STATUS_UNKNOWN);
        item.setMessage("探测未实现（占位）");
        return item;
    }
}
