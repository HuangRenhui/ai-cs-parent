package com.ai.cs.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 网关限流配置
 * 基于 Redis 令牌桶算法
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Configuration
public class RateLimitConfig {

    /**
     * 基于 IP 的限流 Key
     */
    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown"
        );
    }

    /**
     * 基于用户的限流 Key（从请求头获取 userId）
     */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getHeaders().getFirst("X-User-Id") != null
                        ? exchange.getRequest().getHeaders().getFirst("X-User-Id")
                        : "anonymous"
        );
    }

    /**
     * 默认限流器：每秒补充10个令牌，突发容量20
     */
    @Bean
    RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }
}
