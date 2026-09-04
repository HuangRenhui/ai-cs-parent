package com.ai.cs.common.security;

import com.ai.cs.common.util.JwtUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 从 {@code ai.jwt} / {@code JWT_SECRET} 注入签名密钥。网关单独有一份同名配置。
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "ai.jwt")
public class JwtSettings {

    private String secret = "";
    private long expireMs = 24 * 60 * 60 * 1000L;
    private long visitorExpireMs = 2 * 60 * 60 * 1000L;

    @PostConstruct
    public void apply() {
        String resolved = StringUtils.hasText(secret) ? secret : System.getenv("JWT_SECRET");
        JwtUtil.configure(resolved, expireMs, visitorExpireMs);
        if (!StringUtils.hasText(resolved)) {
            log.warn("未配置 JWT_SECRET / ai.jwt.secret，使用内置默认密钥。生产环境必须覆盖。");
        }
    }
}
