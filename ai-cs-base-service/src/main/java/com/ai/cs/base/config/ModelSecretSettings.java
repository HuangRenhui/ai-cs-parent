package com.ai.cs.base.config;

import com.ai.cs.common.security.SecretPolicy;
import com.ai.cs.common.util.SecretCipherUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 模型凭据加密密钥的启动注入。
 *
 * <p>生产、预发与未指定 profile 的环境未配置 AI_MODEL_SECRET 时直接拒绝启动，
 * 避免用仓库内公开的默认密钥加密 apiKey/apiSecret。</p>
 *
 * @author ai-cs
 */
@Component
public class ModelSecretSettings {

    @Value("${ai.model.secret:}")
    private String secret;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @PostConstruct
    public void apply() {
        SecretCipherUtil.configure(secret, SecretPolicy.isLenient(activeProfile));
    }
}
