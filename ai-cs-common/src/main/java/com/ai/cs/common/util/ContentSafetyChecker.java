package com.ai.cs.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内容安全检查工具
 * 检测敏感内容：涉政、色情、未成年、暴力等
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
public class ContentSafetyChecker {
    
    /**
     * 敏感词列表（示例，生产环境应从数据库或外部API获取）
     */
    private static final List<String> SENSITIVE_KEYWORDS = new ArrayList<>();
    
    static {
        // 政治敏感词（示例）
        SENSITIVE_KEYWORDS.add("六四");
        SENSITIVE_KEYWORDS.add("天安门");
        SENSITIVE_KEYWORDS.add("法轮功");
        SENSITIVE_KEYWORDS.add("达赖");
        
        // 暴力/恐怖主义（示例）
        SENSITIVE_KEYWORDS.add("恐怖袭击");
        SENSITIVE_KEYWORDS.add("炸弹");
        SENSITIVE_KEYWORDS.add("爆炸");
        SENSITIVE_KEYWORDS.add("杀人");
        
        // 色情（示例）
        SENSITIVE_KEYWORDS.add("色情");
        SENSITIVE_KEYWORDS.add("淫秽");
        SENSITIVE_KEYWORDS.add("裸体");
        
        // 违法犯罪（示例）
        SENSITIVE_KEYWORDS.add("毒品");
        SENSITIVE_KEYWORDS.add("赌博");
        SENSITIVE_KEYWORDS.add("洗钱");
    }
    
    /**
     * 敏感内容正则模式
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = new ArrayList<>();
    
    static {
        // 检测可能的联系方式（防止诈骗）
        SENSITIVE_PATTERNS.add(Pattern.compile("\\d{11}")); // 手机号
        SENSITIVE_PATTERNS.add(Pattern.compile("\\d{3,4}-\\d{7,8}")); // 固话
        SENSITIVE_PATTERNS.add(Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")); // 邮箱
        
        // 检测可能的银行卡号
        SENSITIVE_PATTERNS.add(Pattern.compile("\\b\\d{16,19}\\b"));
        
        // 检测可能的身份证号
        SENSITIVE_PATTERNS.add(Pattern.compile("\\b\\d{17}[0-9Xx]\\b"));
    }
    
    /**
     * 检查内容是否安全
     *
     * @param content 待检查内容
     * @return 检查结果
     */
    public static SafetyCheckResult check(String content) {
        if (!StringUtils.hasText(content)) {
            return SafetyCheckResult.safe();
        }
        
        String normalized = content.toLowerCase();
        
        // 检查敏感词
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase())) {
                log.warn("检测到敏感词: {}, 内容: {}", keyword, content);
                return SafetyCheckResult.unsafe("内容包含敏感信息");
            }
        }
        
        // 检查敏感模式
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(content).find()) {
                log.warn("检测到敏感模式: {}", pattern.pattern());
                // 联系方式和银行卡号不一定违规，但需要标记
                return SafetyCheckResult.warning("内容可能包含个人隐私信息");
            }
        }
        
        return SafetyCheckResult.safe();
    }
    
    /**
     * 检查内容是否包含个人隐私信息
     *
     * @param content 待检查内容
     * @return 是否包含隐私信息
     */
    public static boolean containsPersonalInfo(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        
        // 检查手机号
        if (Pattern.compile("1[3-9]\\d{9}").matcher(content).find()) {
            return true;
        }
        
        // 检查身份证号
        if (Pattern.compile("\\b\\d{17}[0-9Xx]\\b").matcher(content).find()) {
            return true;
        }
        
        // 检查银行卡号
        if (Pattern.compile("\\b\\d{16,19}\\b").matcher(content).find()) {
            return true;
        }
        
        // 检查邮箱
        if (Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}").matcher(content).find()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 脱敏处理：隐藏个人隐私信息
     *
     * @param content 原始内容
     * @return 脱敏后的内容
     */
    public static String maskPersonalInfo(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        
        String masked = content;
        
        // 脱敏手机号
        masked = masked.replaceAll("1([3-9]\\d)\\d{4}(\\d{4})", "1$1****$2");
        
        // 脱敏身份证号
        masked = masked.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");
        
        // 脱敏银行卡号
        masked = masked.replaceAll("(\\d{4})\\d{8,12}(\\d{4})", "$1********$2");
        
        // 脱敏邮箱
        masked = masked.replaceAll("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})", "$1***@$2");
        
        return masked;
    }
    
    /**
     * 检查内容是否包含恶意链接
     *
     * @param content 待检查内容
     * @return 是否包含恶意链接
     */
    public static boolean containsMaliciousLink(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        
        // 检查URL
        java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile(
            "https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?"
        );
        
        if (urlPattern.matcher(content).find()) {
            // 检查是否为已知恶意域名（示例）
            String lower = content.toLowerCase();
            if (lower.contains("phishing") || lower.contains("malware") || 
                lower.contains("virus") || lower.contains("hack")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 安全检查结果
     */
    public static class SafetyCheckResult {
        private boolean safe;
        private boolean warning;
        private String reason;
        
        public static SafetyCheckResult safe() {
            SafetyCheckResult result = new SafetyCheckResult();
            result.setSafe(true);
            result.setWarning(false);
            return result;
        }
        
        public static SafetyCheckResult unsafe(String reason) {
            SafetyCheckResult result = new SafetyCheckResult();
            result.setSafe(false);
            result.setWarning(false);
            result.setReason(reason);
            return result;
        }
        
        public static SafetyCheckResult warning(String reason) {
            SafetyCheckResult result = new SafetyCheckResult();
            result.setSafe(true);
            result.setWarning(true);
            result.setReason(reason);
            return result;
        }
        
        public boolean isSafe() {
            return safe;
        }
        
        public void setSafe(boolean safe) {
            this.safe = safe;
        }
        
        public boolean isWarning() {
            return warning;
        }
        
        public void setWarning(boolean warning) {
            this.warning = warning;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
