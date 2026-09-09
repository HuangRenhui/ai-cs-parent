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

    /**
     * 每个请求：把 requestId/traceId/sessionId/tenantId 写入 MDC 供日志打印，
     * 并在响应头回传 X-Request-Id 便于客户端排查。
     * 最高优先级执行，保证后续所有过滤器/控制器的日志都带链路字段。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 上游(网关)已带 requestId 则沿用，保证全链路同一 ID；否则新生成
        String requestId = headerOrNew(request, REQUEST_ID);
        MDC.put("requestId", requestId);
        putIfPresent(request, "X-Trace-Id", "traceId");
        putIfPresent(request, "X-Session-Id", "sessionId");
        putIfPresent(request, "X-Tenant-Id", "tenantId");
        // 没有独立 traceId 时用 requestId 顶上，保证日志检索字段不为空
        if (!StringUtils.hasText(MDC.get("traceId"))) {
            MDC.put("traceId", requestId);
        }
        response.setHeader(REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理：线程池复用，残留 MDC 会串到下一个请求
            MDC.clear();
        }
    }

    /** 取请求头，缺失时生成 32 位无横线 UUID 作为新 requestId */
    private static String headerOrNew(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value.trim() : UUID.randomUUID().toString().replace("-", "");
    }

    /** 请求头存在才写入 MDC */
    private static void putIfPresent(HttpServletRequest request, String header, String mdcKey) {
        String value = request.getHeader(header);
        if (StringUtils.hasText(value)) {
            MDC.put(mdcKey, value.trim());
        }
    }
}
