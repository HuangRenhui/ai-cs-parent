package com.ai.cs.aiagent.service;

import com.ai.cs.api.feign.BaseServiceFeign;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 可配置意图识别服务（占位）
 *
 * <p>TODO 后续实现：先经 {@code BaseServiceFeign.getEnabledIntents(tenantCode)} 拉取租户自定义意图
 * （失败时回退硬编码 {@code IntentEnum}），据此构建分类器提示词（含意图清单与各自说明、entity 抽取要求），
 * 调用 INTENT 类型模型后解析 JSON 并按「意图名 → 意图码 → 关键词」归一化，非法值回退「咨询」。</p>
 *
 * <p>当前不拉配置、不调用模型：意图恒返回「咨询」降级结果（{@code llmDegraded=true}）。</p>
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@Service
public class ConfigurableIntentService {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private BaseServiceFeign baseServiceFeign;

    /**
     * 意图识别（占位：不调用模型与配置服务，降级为「咨询」）
     *
     * @param userMsg    用户消息
     * @param tenantCode 租户编码
     * @return 降级意图（llmDegraded=true）
     */
    public IntentDTO getIntent(String userMsg, String tenantCode) {
        log.warn("[占位] 可配置意图识别未实现 tenantCode={}，降级为「咨询」（llmDegraded=true）", tenantCode);
        return fallbackConsult();
    }

    /**
     * 降级意图：统一回退为「咨询」，并打上 llmDegraded 标记供上层识别
     */
    private IntentDTO fallbackConsult() {
        IntentDTO dto = new IntentDTO();
        dto.setIntent("咨询");
        dto.setEntity("");
        dto.setLlmDegraded(true);
        return dto;
    }
}
