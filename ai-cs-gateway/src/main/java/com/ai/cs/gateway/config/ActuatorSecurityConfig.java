package com.ai.cs.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * Actuator 端点安全过滤器
 * 为监控端点添加基本的认证保护
 *
 * @author huangrenhui
 * @date 2026/9/11
 */
@Slf4j
@Component
public class ActuatorSecurityConfig extends AbstractGatewayFilterFactory<Object> {

    @Value("${actuator.security.token:${ACTUATOR_TOKEN:}}")
    private String actuatorToken;

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // 只对 /actuator 路径进行认证检查
            if (!path.startsWith("/actuator")) {
                return chain.filter(exchange);
            }

            String token = exchange.getRequest().getHeaders().getFirst("X-Actuator-Token");
            String resolvedToken = resolveActuatorToken();

            if (resolvedToken != null && !resolvedToken.isBlank() && !resolvedToken.equals(token)) {
                log.warn("Actuator 访问被拒绝：无效的令牌，路径：{}", path);
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }

    private String resolveActuatorToken() {
        if (actuatorToken != null && !actuatorToken.isBlank()) {
            return actuatorToken.trim();
        }
        String env = System.getenv("ACTUATOR_TOKEN");
        return env != null ? env.trim() : null;
    }
}
