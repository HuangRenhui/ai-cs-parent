package com.ai.cs.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 网关生成并下传 X-Request-Id / X-Trace-Id。
 */
@Component
public class RequestIdGatewayFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TRACE_ID = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = headerOrNew(exchange.getRequest(), REQUEST_ID);
        String incomingTrace = exchange.getRequest().getHeaders().getFirst(TRACE_ID);
        String traceId = StringUtils.hasText(incomingTrace) ? incomingTrace.trim() : requestId;
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(REQUEST_ID, requestId)
                .header(TRACE_ID, traceId)
                .build();
        exchange.getResponse().getHeaders().set(REQUEST_ID, requestId);
        exchange.getResponse().getHeaders().set(TRACE_ID, traceId);
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signal -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String headerOrNew(ServerHttpRequest request, String name) {
        String value = request.getHeaders().getFirst(name);
        return StringUtils.hasText(value) ? value.trim() : UUID.randomUUID().toString().replace("-", "");
    }
}
