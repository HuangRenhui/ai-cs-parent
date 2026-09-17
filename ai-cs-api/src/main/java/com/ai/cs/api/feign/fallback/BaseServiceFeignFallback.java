package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.BaseServiceFeign;
import com.ai.cs.common.dto.IntentConfigDTO;
import com.ai.cs.common.dto.SlotFillingDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基础服务 Feign 降级
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class BaseServiceFeignFallback implements BaseServiceFeign {

    /** 降级：客户查询失败，返回 503 提示调用方稍后重试 */
    @Override
    public Result<Object> getCustomerById(Long id) {
        log.error("基础服务调用失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用，请稍后重试");
    }

    /** 降级：用户信息查询失败，返回 503 提示调用方稍后重试 */
    @Override
    public Result<Object> getUserInfo() {
        log.error("基础服务调用失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用，请稍后重试");
    }

    /** 降级：意图配置拉取失败，返回空列表由 Agent 回退硬编码意图 */
    @Override
    public Result<List<IntentConfigDTO>> getEnabledIntents(String tenantCode) {
        log.error("意图配置拉取失败，触发熔断降级 tenantCode={}", tenantCode);
        return Result.fail(503, "基础服务暂时不可用，请稍后重试");
    }

    /** 降级：槽位配置拉取失败，返回空列表跳过填槽以免阻塞对话 */
    @Override
    public Result<List<SlotFillingDTO>> getSlotsByIntent(String tenantCode, String intentCode) {
        log.error("槽位配置拉取失败，触发熔断降级 tenantCode={} intentCode={}", tenantCode, intentCode);
        return Result.fail(503, "基础服务暂时不可用，请稍后重试");
    }
}
