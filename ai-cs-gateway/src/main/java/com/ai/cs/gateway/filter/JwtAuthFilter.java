package com.ai.cs.gateway.filter;

import com.ai.cs.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway JWT认证全局过滤器
 * 对所有请求进行JWT Token校验，白名单路径跳过
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** 无需认证的路径白名单 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/ws/",
            "/doc.html",
            "/webjars/",
            "/v3/api-docs",
            "/swagger-resources"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单路径直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 提取Token
        String token = extractToken(exchange.getRequest());
        if (token == null || !JwtUtil.validateToken(token)) {
            log.warn("未授权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 将用户信息添加到请求头传递给下游服务
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Username", username != null ? username : "")
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return request.getQueryParams().getFirst("token");
    }
}
