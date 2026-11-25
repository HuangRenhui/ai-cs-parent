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

    /** 仅本机/开发环境使用的演示令牌；生产与空 profile 禁止使用 */
    static final String DEV_DEFAULT_TOKEN = "AiCsInternalToken2026LocalOnly";

    /** 内部令牌：优先环境变量 INTERNAL_API_TOKEN，其次配置 ai.internal.token */
    @Value("${ai.internal.token:${INTERNAL_API_TOKEN:}}")
    private String token;

    /** 当前激活的 Spring 配置，用于决定是否允许演示令牌 */
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /** 启动时解析并冻结的令牌值 */
    private volatile String resolvedToken;

    /**
     * 启动时解析令牌：显式配置优先；未配置仅允许 dev/local/test 回退到演示令牌。
     * 生产、预发与未指定 profile 的环境必须显式配置，否则拒绝启动。
     */
    @PostConstruct
    public void init() {
        boolean lenient = SecretPolicy.isLenient(activeProfile);
        String configured = StringUtils.hasText(token) ? token.trim() : null;
        if (configured == null) {
            configured = System.getenv("INTERNAL_API_TOKEN");
            configured = StringUtils.hasText(configured) ? configured.trim() : null;
        }
        if (configured == null) {
            if (!lenient) {
                throw new IllegalStateException(
                        "未配置 INTERNAL_API_TOKEN / ai.internal.token（当前 profile=" + activeProfile
                                + "）。请显式注入内部令牌，或本地开发使用 dev/local profile。");
            }
            resolvedToken = DEV_DEFAULT_TOKEN;
            log.warn("未配置 INTERNAL_API_TOKEN，dev/local 环境使用内置演示内部令牌。");
            return;
        }
        assertAcceptable(configured, lenient);
        resolvedToken = configured;
    }

    /** 严格模式拒绝仓库演示令牌与过短令牌（过短可被暴力枚举） */
    private void assertAcceptable(String value, boolean lenient) {
        if (lenient) {
            return;
        }
        if (DEV_DEFAULT_TOKEN.equals(value)) {
            throw new IllegalStateException("禁止在生产/未指定 profile 的环境使用内置默认内部令牌");
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("内部令牌过短，生产环境要求至少 16 字节");
        }
    }

    /** 解析后的内部令牌（启动时已确定，运行期不再变化） */
    public String resolveToken() {
        if (resolvedToken != null) {
            return resolvedToken;
        }
        // init 未执行（非 Spring 生命周期）时保持 fail-closed，避免静默使用演示令牌
        if (!SecretPolicy.isLenient(activeProfile)) {
            throw new IllegalStateException("内部令牌尚未初始化，拒绝使用内置默认值");
        }
        return DEV_DEFAULT_TOKEN;
    }

    /** 校验对端传来的内部令牌是否与本服务配置一致（常量时间比较，避免时序侧信道） */
    public boolean matches(String incoming) {
        if (!StringUtils.hasText(incoming)) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                incoming.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                resolveToken().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
