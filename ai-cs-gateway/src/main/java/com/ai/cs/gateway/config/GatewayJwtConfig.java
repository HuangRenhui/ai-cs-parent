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

    @Value("${ai.jwt.secret:}")
    private String secret;

    @Value("${ai.jwt.expire-ms:86400000}")
    private long expireMs;

    @Value("${ai.jwt.visitor-expire-ms:7200000}")
    private long visitorExpireMs;

    @PostConstruct
    public void apply() {
        String resolved = StringUtils.hasText(secret) ? secret : System.getenv("JWT_SECRET");
        JwtUtil.configure(resolved, expireMs, visitorExpireMs);
        if (!StringUtils.hasText(resolved)) {
            log.warn("网关未配置 JWT_SECRET / ai.jwt.secret，使用内置默认密钥。生产环境必须覆盖。");
        }
    }
}
