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
 * Gateway JWT 全局过滤器。访客令牌只能访问聊天相关路径。
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** 白名单路径（前缀匹配）：登录、注册、WebSocket、健康检查等无需认证即可访问 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/auth/login",
            "/auth/register",
            "/ws/",
            "/open/widget/init",
            "/ops/health/self",
            "/knowledge/health",
            "/files/avatars/",
            "/actuator/health"
    );

    /** 访客令牌允许访问的路径（前缀匹配）：只开放聊天与会话相关接口 */
    private static final List<String> VISITOR_ALLOWED = List.of(
            "/ai/chat",
            "/session/ensure",
            "/session/message"
    );

    /**
     * JWT 全局鉴权：白名单放行 → 校验令牌 → 访客令牌限制路径 → 注入用户身份请求头后放行
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单直接放行，不做任何令牌校验
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 无令牌或令牌无效：返回 401，请求到此终止不再进入过滤器链
        String token = extractToken(exchange.getRequest());
        if (token == null || !JwtUtil.validateToken(token)) {
            log.warn("未授权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 访客令牌权限收窄：只能访问聊天相关路径，越权访问返回 403
        if (JwtUtil.isVisitor(token) && !isVisitorAllowed(path)) {
            log.warn("访客令牌越权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 把令牌中的用户身份解析后注入请求头，下游服务无需再解析 JWT
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);
        String typ = JwtUtil.getTokenType(token);
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Username", username != null ? username : "")
                .header("X-Token-Type", typ != null ? typ : JwtUtil.TYP_STAFF)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * 过滤器顺序：-100，在请求 ID 生成之后、日志记录之前（日志过滤器为 -200，实际先于本过滤器执行）
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /** 判断路径是否命中白名单（前缀匹配） */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /** 判断路径是否为访客可访问路径（前缀匹配） */
    private boolean isVisitorAllowed(String path) {
        return VISITOR_ALLOWED.stream().anyMatch(path::startsWith);
    }

    /**
     * 提取令牌：优先取 Authorization: Bearer 头，其次取查询参数 token（兼容 WebSocket 握手等无法带头的场景）
     */
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return request.getQueryParams().getFirst("token");
    }
}
