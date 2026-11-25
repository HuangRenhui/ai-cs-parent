package com.ai.cs.open.config;

import com.ai.cs.common.security.SecretPolicy;
import com.ai.cs.common.util.SecretCipherUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 连接器/Webhook 凭据加密密钥的启动注入。
 *
 * <p>与 base-service 使用同一个 AI_MODEL_SECRET；生产、预发与空 profile 未配置时拒绝启动。</p>
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
