package com.ai.cs.gateway.config;

import com.ai.cs.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 网关不扫描 common 组件，在此单独把密钥灌进 {@link JwtUtil}。
 */
@Slf4j
@Component
public class GatewayJwtConfig {

    /** JWT 签名密钥（配置项 ai.jwt.secret，缺省时回退环境变量 JWT_SECRET） */
    @Value("${ai.jwt.secret:}")
    private String secret;

    /** 员工令牌有效期（毫秒），默认 24 小时 */
    @Value("${ai.jwt.expire-ms:86400000}")
    private long expireMs;

    /** 访客令牌有效期（毫秒），默认 2 小时 */
    @Value("${ai.jwt.visitor-expire-ms:7200000}")
    private long visitorExpireMs;

    /**
     * 启动时把密钥与有效期注入 {@link JwtUtil}（静态工具类无法走 Spring 注入，故在此显式配置）
     */
    @PostConstruct
    public void apply() {
        // 配置项优先，环境变量兜底
        String resolved = StringUtils.hasText(secret) ? secret : System.getenv("JWT_SECRET");
        JwtUtil.configure(resolved, expireMs, visitorExpireMs);
        if (!StringUtils.hasText(resolved)) {
            log.warn("网关未配置 JWT_SECRET / ai.jwt.secret，使用内置默认密钥。生产环境必须覆盖。");
        }
    }
}
