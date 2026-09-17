package com.ai.cs.open.support;

import com.ai.cs.common.security.InternalAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;

/**
 * 调用基础服务做匿名会话合并（占位）。
 *
 * <p>TODO 后续实现：向 {@code {feign.base-service.url}/session/merge-anonymous} POST
 * {@code SessionDTO(visitorRef, customerId)}，带内部鉴权头 {@code InternalAuthProperties.HEADER}；
 * 失败只记日志，不阻断 Widget 初始化。不用 Feign 是为避免 open 模块依赖 ai-cs-api。</p>
 *
 * <p>当前不发请求：{@link #mergeAnonymous} 只记日志，因此访客登录后历史匿名会话不会自动合并
 * （显式调用内核接口 {@code /session/merge-anonymous} 仍可用）。</p>
 */
@Slf4j
@Component
public class BaseSessionClient {

    @Value("${feign.base-service.url:http://localhost:8084}")
    private String baseUrl;

    @Resource
    private InternalAuthProperties internalAuthProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 把 visitorRef 下 customerId=0 的会话挂到已登录客户（占位：不发请求）
     *
     * @param visitorRef 外部访客标识
     * @param customerId 登录后的内部客户 ID
     */
    public void mergeAnonymous(String visitorRef, Long customerId) {
        log.info("[占位] 匿名会话自动合并未实现，不发请求 visitorRef={} customerId={}", visitorRef, customerId);
    }
}
