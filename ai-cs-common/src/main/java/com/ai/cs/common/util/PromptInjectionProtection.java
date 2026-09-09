package com.ai.cs.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 提示词注入防护工具
 * 检测和防止用户通过精心构造的输入绕过系统规则
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
public class PromptInjectionProtection {
    
    /**
     * 常见的提示词注入模式
     */
    private static final List<Pattern> INJECTION_PATTERNS = new ArrayList<>();
    
    static {
        // 忽略指令模式
        INJECTION_PATTERNS.add(Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|the)\\s+(instructions|rules|prompts)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|above|the)\\s+(instructions|rules|prompts)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)forget\\s+(all\\s+)?(previous|above|the)\\s+(instructions|rules|prompts)"));
        
        // 角色扮演模式
        INJECTION_PATTERNS.add(Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+\\w+"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)act\\s+as\\s+(a|an)\\s+\\w+"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)pretend\\s+to\\s+be\\s+(a|an)\\s+\\w+"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)roleplay\\s+as\\s+(a|an)\\s+\\w+"));
        
        // 系统指令覆盖模式
        INJECTION_PATTERNS.add(Pattern.compile("(?i)new\\s+(instruction|rule|prompt)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)override\\s+(the\\s+)?(instruction|rule|prompt)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)replace\\s+(the\\s+)?(instruction|rule|prompt)"));
        
        // 输出控制模式
        INJECTION_PATTERNS.add(Pattern.compile("(?i)print\\s+(the\\s+)?(instruction|rule|prompt)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)show\\s+(the\\s+)?(instruction|rule|prompt)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)repeat\\s+(the\\s+)?(instruction|rule|prompt)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)output\\s+(the\\s+)?(instruction|rule|prompt)"));
        
        // 绕过限制模式
        INJECTION_PATTERNS.add(Pattern.compile("(?i)bypass\\s+(the\\s+)?(restriction|limit|rule)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)circumvent\\s+(the\\s+)?(restriction|limit|rule)"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)ignore\\s+(the\\s+)?(restriction|limit|rule)"));
        
        // 代码注入模式
        INJECTION_PATTERNS.add(Pattern.compile("(?i)execute\\s+(this\\s+)?code"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)run\\s+(this\\s+)?code"));
        INJECTION_PATTERNS.add(Pattern.compile("(?i)eval\\s+(this\\s+)?code"));
        
        // 特殊字符注入
        INJECTION_PATTERNS.add(Pattern.compile("<\\|.*?\\|>"));
        INJECTION_PATTERNS.add(Pattern.compile("\\[\\[.*?\\]\\]"));
    }
    
    /**
     * 检测输入是否包含提示词注入
     *
     * @param input 用户输入
     * @return 是否检测到注入
     */
    public static boolean detectInjection(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        
        String normalized = input.toLowerCase().trim();
        
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.warn("检测到提示词注入尝试: {}", input);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检测输入是否包含敏感操作指令
     * 例如：忽略规则去退款、绕过权限等
     *
     * @param input 用户输入
     * @param sensitiveActions 敏感操作列表（如"退款"、"冻卡"等）
     * @return 是否检测到敏感操作尝试
     */
    public static boolean detectSensitiveOperationAttempt(String input, List<String> sensitiveActions) {
        if (!StringUtils.hasText(input) || sensitiveActions == null || sensitiveActions.isEmpty()) {
            return false;
        }
        
        String normalized = input.toLowerCase();
        
        // 检查是否包含绕过指令
        if (normalized.contains("忽略") || normalized.contains("不管") || 
            normalized.contains("不用") || normalized.contains("跳过") ||
            normalized.contains("ignore") || normalized.contains("skip") ||
            normalized.contains("bypass")) {
            
            // 检查是否包含敏感操作
            for (String action : sensitiveActions) {
                if (normalized.contains(action.toLowerCase())) {
                    log.warn("检测到敏感操作绕过尝试: {}, 操作: {}", input, action);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 清理输入，移除潜在的注入内容
     *
     * @param input 用户输入
     * @return 清理后的输入
     */
    public static String sanitizeInput(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        // 移除markdown代码块标记
        String sanitized = input.replaceAll("```[a-z]*\\n?", "");
        sanitized = sanitized.replaceAll("```", "");
        
        // 移除特殊控制字符
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        
        return sanitized;
    }
    
    /**
     * 验证输入长度，防止超长输入攻击
     *
     * @param input 用户输入
     * @param maxLength 最大长度
     * @return 是否有效
     */
    public static boolean validateLength(String input, int maxLength) {
        if (!StringUtils.hasText(input)) {
            return true;
        }
        return input.length() <= maxLength;
    }
    
    /**
     * 检测输入是否包含重复字符（防止垃圾输入）
     *
     * @param input 用户输入
     * @param threshold 重复阈值（0-1之间）
     * @return 是否包含过多重复
     */
    public static boolean detectExcessiveRepetition(String input, double threshold) {
        if (!StringUtils.hasText(input) || input.length() < 10) {
            return false;
        }
        
        // 计算字符重复率
        java.util.Map<Character, Integer> charCount = new java.util.HashMap<>();
        for (char c : input.toCharArray()) {
            charCount.put(c, charCount.getOrDefault(c, 0) + 1);
        }
        
        int maxCount = charCount.values().stream().max(Integer::compare).orElse(0);
        double repetitionRate = (double) maxCount / input.length();
        
        if (repetitionRate > threshold) {
            log.warn("检测到过多重复字符: {}, 重复率: {}", input, repetitionRate);
            return true;
        }
        
        return false;
    }
    
    /**
     * 综合安全检查
     *
     * @param input 用户输入
     * @param maxLength 最大长度
     * @param sensitiveActions 敏感操作列表
     * @return 安全检查结果
     */
    public static SecurityCheckResult performSecurityCheck(String input, int maxLength, List<String> sensitiveActions) {
        SecurityCheckResult result = new SecurityCheckResult();
        result.setSafe(true);
        
        if (!validateLength(input, maxLength)) {
            result.setSafe(false);
            result.setReason("输入长度超过限制");
            return result;
        }
        
        if (detectInjection(input)) {
            result.setSafe(false);
            result.setReason("检测到提示词注入");
            return result;
        }
        
        if (detectSensitiveOperationAttempt(input, sensitiveActions)) {
            result.setSafe(false);
            result.setReason("检测到敏感操作绕过尝试");
            return result;
        }
        
        if (detectExcessiveRepetition(input, 0.7)) {
            result.setSafe(false);
            result.setReason("输入包含过多重复字符");
            return result;
        }
        
        return result;
    }
    
    /**
     * 安全检查结果
     */
    public static class SecurityCheckResult {
        private boolean safe;
        private String reason;
        
        public boolean isSafe() {
            return safe;
        }
        
        public void setSafe(boolean safe) {
            this.safe = safe;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
