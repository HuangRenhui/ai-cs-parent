package com.ai.cs.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个 HTTP 请求写入 MDC，并回传 X-Request-Id。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = headerOrNew(request, REQUEST_ID);
        MDC.put("requestId", requestId);
        putIfPresent(request, "X-Trace-Id", "traceId");
        putIfPresent(request, "X-Session-Id", "sessionId");
        putIfPresent(request, "X-Tenant-Id", "tenantId");
        if (!StringUtils.hasText(MDC.get("traceId"))) {
            MDC.put("traceId", requestId);
        }
        response.setHeader(REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String headerOrNew(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value.trim() : UUID.randomUUID().toString().replace("-", "");
    }

    private static void putIfPresent(HttpServletRequest request, String header, String mdcKey) {
        String value = request.getHeader(header);
        if (StringUtils.hasText(value)) {
            MDC.put(mdcKey, value.trim());
        }
    }
}
