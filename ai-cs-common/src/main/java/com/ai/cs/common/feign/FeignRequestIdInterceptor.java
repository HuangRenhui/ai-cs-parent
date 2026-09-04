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

    @Override
    public void apply(RequestTemplate template) {
        put(template, "X-Request-Id", "requestId");
        put(template, "X-Trace-Id", "traceId");
        put(template, "X-Session-Id", "sessionId");
        put(template, "X-Tenant-Id", "tenantId");
    }

    private static void put(RequestTemplate template, String header, String mdcKey) {
        String value = MDC.get(mdcKey);
        if (StringUtils.hasText(value)) {
            template.header(header, value);
        }
    }
}
