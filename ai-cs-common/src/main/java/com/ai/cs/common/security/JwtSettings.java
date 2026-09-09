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

    /** 签名密钥，空则回退环境变量 JWT_SECRET，再回退内置默认（仅本机） */
    private String secret = "";
    /** 员工(管理端) token 有效期，默认 24 小时 */
    private long expireMs = 24 * 60 * 60 * 1000L;
    /** 访客(C 端聊窗) token 有效期，默认 2 小时 */
    private long visitorExpireMs = 2 * 60 * 60 * 1000L;

    /** 启动时把配置注入 JwtUtil 静态上下文；未配置密钥时打告警（默认密钥不能上生产） */
    @PostConstruct
    public void apply() {
        String resolved = StringUtils.hasText(secret) ? secret : System.getenv("JWT_SECRET");
        JwtUtil.configure(resolved, expireMs, visitorExpireMs);
        if (!StringUtils.hasText(resolved)) {
            log.warn("未配置 JWT_SECRET / ai.jwt.secret，使用内置默认密钥。生产环境必须覆盖。");
        }
    }
}
