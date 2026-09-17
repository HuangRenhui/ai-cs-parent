package com.ai.cs.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一安全响应头：防止 MIME 嗅探、点击劫持，并提示浏览器启用 XSS 过滤。
 */
@Component
public class SecureHeadersFilter implements GlobalFilter, Ordered {

    /**
     * 在响应写回前补齐安全头；已有同名头不覆盖，避免与下游冲突。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.putIfAbsent("X-Content-Type-Options", java.util.List.of("nosniff"));
            headers.putIfAbsent("X-Frame-Options", java.util.List.of("SAMEORIGIN"));
            headers.putIfAbsent("X-XSS-Protection", java.util.List.of("1; mode=block"));
            headers.putIfAbsent("Referrer-Policy", java.util.List.of("strict-origin-when-cross-origin"));
            headers.putIfAbsent("Cache-Control", java.util.List.of("no-store"));
            // 新增安全头：HSTS、CSP、Permissions-Policy
            headers.putIfAbsent("Strict-Transport-Security", java.util.List.of("max-age=31536000; includeSubDomains"));
            headers.putIfAbsent("Content-Security-Policy", java.util.List.of(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'self';"
            ));
            headers.putIfAbsent("Permissions-Policy", java.util.List.of(
                    "geolocation=(), " +
                    "microphone=(), " +
                    "camera=(), " +
                    "payment=(), " +
                    "usb=(), " +
                    "magnetometer=(), " +
                    "gyroscope=()"
            ));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /** 靠后执行，保证能改到最终响应头 */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
