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
        // 取客户端真实 IP 作为限流维度；取不到（如本地调用）时统一归入 "unknown" 桶
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
        // 以网关注入的 X-User-Id 为限流维度；未登录请求统一归入 "anonymous" 桶
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
        // 参数依次为：补充速率、突发容量、每次请求消耗令牌数
        return new RedisRateLimiter(10, 20, 1);
    }
}
