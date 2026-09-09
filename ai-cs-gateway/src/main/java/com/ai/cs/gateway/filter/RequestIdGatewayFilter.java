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

    /** 请求 ID 请求头名（全链路日志追踪主键） */
    public static final String REQUEST_ID = "X-Request-Id";
    /** 链路追踪 ID 请求头名（缺省时与请求 ID 同值） */
    public static final String TRACE_ID = "X-Trace-Id";

    /**
     * 为每个请求生成/透传 X-Request-Id 与 X-Trace-Id：
     * 请求头改写后下传给下游服务，同时写入响应头与 MDC，保证日志可串联
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 上游已带请求 ID 则沿用，否则新生成，保证同一请求全链路 ID 一致
        String requestId = headerOrNew(exchange.getRequest(), REQUEST_ID);
        String incomingTrace = exchange.getRequest().getHeaders().getFirst(TRACE_ID);
        // 没有独立 traceId 时退化使用 requestId，减少一次 ID 生成
        String traceId = StringUtils.hasText(incomingTrace) ? incomingTrace.trim() : requestId;
        // 改写请求头，向下游服务传递两个 ID
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(REQUEST_ID, requestId)
                .header(TRACE_ID, traceId)
                .build();
        // 同步写入响应头，方便调用方/前端排查问题时回报 ID
        exchange.getResponse().getHeaders().set(REQUEST_ID, requestId);
        exchange.getResponse().getHeaders().set(TRACE_ID, traceId);
        // 写入 MDC 让本请求的日志都带上 ID；请求结束后必须清理，防止线程复用导致串号
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signal -> MDC.clear());
    }

    /**
     * 最高优先级：保证 ID 生成先于鉴权、日志等其他过滤器执行
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 读取指定请求头；为空时生成 32 位无横线 UUID 作为新 ID
     */
    private static String headerOrNew(ServerHttpRequest request, String name) {
        String value = request.getHeaders().getFirst(name);
        return StringUtils.hasText(value) ? value.trim() : UUID.randomUUID().toString().replace("-", "");
    }
}
