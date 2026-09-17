package com.ai.cs.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求日志过滤器
 * 记录所有请求的路径、方法和响应状态
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    /**
     * 记录请求访问日志：在响应结束（doFinally）时统一输出方法、路径、状态码与耗时，
     * 保证无论成功/异常/取消都能留下访问记录
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        return chain.filter(exchange).doFinally(signalType -> {
            long duration = System.currentTimeMillis() - start;
            // 状态码可能尚未写出（如连接中断），兜底记为 0
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
            // 关联 RequestIdGatewayFilter 注入的请求 ID，便于全链路串联日志
            String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
            log.info("[Gateway] {} {} -> {} ({}ms) requestId={}", method, path, status, duration, requestId);
        });
    }

    /**
     * 过滤器顺序：-200 保证日志过滤器在 JWT 鉴权（-100）之前执行，未授权请求也能被记录
     */
    @Override
    public int getOrder() {
        return -200;
    }
}
