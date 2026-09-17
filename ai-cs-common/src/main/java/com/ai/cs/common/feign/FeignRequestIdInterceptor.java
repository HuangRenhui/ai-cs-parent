package com.ai.cs.common.feign;

import com.ai.cs.common.security.InternalAuthProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将当前请求的 requestId / traceId 以及内部调用令牌传递给下游 Feign。
 */
@Component
public class FeignRequestIdInterceptor implements RequestInterceptor {

    private final InternalAuthProperties internalAuthProperties;

    public FeignRequestIdInterceptor(InternalAuthProperties internalAuthProperties) {
        this.internalAuthProperties = internalAuthProperties;
    }

    /** 每次 Feign 调用前，把 MDC 中的链路字段与内部令牌复制到下游请求头 */
    @Override
    public void apply(RequestTemplate template) {
        put(template, "X-Request-Id", "requestId");
        put(template, "X-Trace-Id", "traceId");
        put(template, "X-Session-Id", "sessionId");
        put(template, "X-Tenant-Id", "tenantId");
        // 内部令牌：下游据此识别服务间调用，拒绝伪造的 X-User-Id
        String token = internalAuthProperties.resolveToken();
        if (StringUtils.hasText(token)) {
            template.header(InternalAuthProperties.HEADER, token);
        }
    }

    /** MDC 值非空才写入请求头，避免覆盖下游已有的同名头 */
    private static void put(RequestTemplate template, String header, String mdcKey) {
        String value = MDC.get(mdcKey);
        if (StringUtils.hasText(value)) {
            template.header(header, value);
        }
    }
}
