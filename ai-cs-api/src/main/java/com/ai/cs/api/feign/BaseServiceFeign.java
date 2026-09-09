package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.BaseServiceFeignFallback;
import com.ai.cs.base.entity.IntentConfig;
import com.ai.cs.base.entity.SlotFilling;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 基础服务 Feign 接口
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@FeignClient(value = "ai-cs-base-service", url = "${feign.base-service.url:http://localhost:8084}", fallback = BaseServiceFeignFallback.class)
public interface BaseServiceFeign {

    /** 按 ID 查询客户信息 */
    @GetMapping("/customer/{id}")
    Result<Object> getCustomerById(@PathVariable("id") Long id);

    /** 获取当前登录用户信息（依赖网关注入的认证上下文） */
    @GetMapping("/auth/userinfo")
    Result<Object> getUserInfo();

    /** 获取租户启用的意图列表 */
    @GetMapping("/system/intent/list")
    Result<List<IntentConfig>> getEnabledIntents(@RequestParam(required = false) String tenantCode);

    /** 获取意图关联的槽位列表 */
    @GetMapping("/system/slot/list")
    Result<List<SlotFilling>> getSlotsByIntent(@RequestParam(required = false) String tenantCode,
                                              @RequestParam(required = false) String intentCode);
}
