package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.BaseServiceFeign;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}
