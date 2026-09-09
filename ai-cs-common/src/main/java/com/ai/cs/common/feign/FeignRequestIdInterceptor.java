package com.ai.cs.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将当前请求的 requestId / traceId 传递给下游 Feign 调用。
 */
@Component
public class FeignRequestIdInterceptor implements RequestInterceptor {

    /** 每次 Feign 调用前，把 MDC 中的链路字段复制到下游请求头 */
    @Override
    public void apply(RequestTemplate template) {
        put(template, "X-Request-Id", "requestId");
        put(template, "X-Trace-Id", "traceId");
        put(template, "X-Session-Id", "sessionId");
        put(template, "X-Tenant-Id", "tenantId");
    }

    /** MDC 值非空才写入请求头，避免覆盖下游已有的同名头 */
    private static void put(RequestTemplate template, String header, String mdcKey) {
        String value = MDC.get(mdcKey);
        if (StringUtils.hasText(value)) {
            template.header(header, value);
        }
    }
}
