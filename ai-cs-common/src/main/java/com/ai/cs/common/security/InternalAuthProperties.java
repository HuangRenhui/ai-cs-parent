package com.ai.cs.common.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 服务间内部调用凭证。网关与 Feign 注入此令牌，下游据此信任 X-User-Id，防止绕过网关伪造身份。
 */
@Data
@Slf4j
@Component
@ConditionalOnWebApplication
public class InternalAuthProperties {

    /** 内部调用请求头名 */
    public static final String HEADER = "X-Internal-Token";

    /** 仅本机演示用的默认内部令牌，生产必须通过 INTERNAL_API_TOKEN 覆盖 */
    static final String DEFAULT_TOKEN = "AiCsInternalToken2026LocalOnly";

    /** 内部令牌：优先环境变量 INTERNAL_API_TOKEN，其次配置 ai.internal.token */
    @Value("${ai.internal.token:${INTERNAL_API_TOKEN:}}")
    private String token;

    /** 当前激活的 Spring 配置，用于生产环境拒绝默认令牌 */
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /** 解析后的令牌；空配置时回退默认值 */
    public String resolveToken() {
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        String env = System.getenv("INTERNAL_API_TOKEN");
        if (StringUtils.hasText(env)) {
            return env.trim();
        }
        return DEFAULT_TOKEN;
    }

    /** 校验对端传来的内部令牌是否与本服务配置一致 */
    public boolean matches(String incoming) {
        return StringUtils.hasText(incoming) && incoming.equals(resolveToken());
    }

    /** 生产环境禁止使用内置默认内部令牌，避免伪造下游身份 */
    @PostConstruct
    public void warnIfDefault() {
        String resolved = resolveToken();
        boolean prod = activeProfile != null && activeProfile.toLowerCase().contains("prod");
        if (DEFAULT_TOKEN.equals(resolved)) {
            if (prod) {
                throw new IllegalStateException("生产环境必须配置 INTERNAL_API_TOKEN / ai.internal.token，禁止使用内置默认内部令牌");
            }
            log.warn("未配置 INTERNAL_API_TOKEN，使用内置默认内部令牌。生产环境必须覆盖。");
        }
    }
}
