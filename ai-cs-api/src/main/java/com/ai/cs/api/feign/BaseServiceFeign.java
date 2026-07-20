package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.BaseServiceFeignFallback;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 基础服务 Feign 接口
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@FeignClient(value = "ai-cs-base-service", fallback = BaseServiceFeignFallback.class)
public interface BaseServiceFeign {

    @GetMapping("/customer/{id}")
    Result<Object> getCustomerById(@PathVariable("id") Long id);

    @GetMapping("/user/info")
    Result<Object> getUserInfo();
}
