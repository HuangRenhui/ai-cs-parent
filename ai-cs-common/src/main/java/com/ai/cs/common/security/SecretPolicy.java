package com.ai.cs.common.security;

import java.util.Locale;

/**
 * 密钥/内部令牌的启动策略。
 *
 * <p>允许使用内置演示密钥的场景只有显式的本地/开发/测试环境：{@code dev}、{@code local}、{@code test}。
 * 其余情况（生产、预发、以及<b>未显式指定 profile</b>）一律按严格模式处理，宁可启动失败，
 * 也不允许带着仓库内公开的默认密钥跑起来。</p>
 *
 * @author ai-cs
 */
public final class SecretPolicy {

    private SecretPolicy() {
    }

    /**
     * 是否允许回退到内置演示密钥。
     *
     * @param activeProfile spring.profiles.active（可空）
     * @return true=本地/开发/测试环境，允许兜底；false=必须显式配置
     */
    public static boolean isLenient(String activeProfile) {
        if (activeProfile == null || activeProfile.isBlank()) {
            return false;
        }
        boolean lenient = false;
        // 支持多 profile（spring.profiles.active=dev,local）：逐个精确匹配，避免 "devops"/"contest" 被误判
        for (String raw : activeProfile.split(",")) {
            String name = raw.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            // prod / production / preprod / prod-cn 一律严格
            if (name.contains("prod")) {
                return false;
            }
            if ("dev".equals(name) || "local".equals(name) || "test".equals(name)) {
                lenient = true;
            }
        }
        return lenient;
    }
}
