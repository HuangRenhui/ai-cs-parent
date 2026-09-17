package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 调试配置属性类
 * 支持角色限定、格式约束、CoT思维链、Few-shot少样本、边界约束、上下文约束六大策略
 *
 * @author huangrenhui
 * @date 2026/7/27
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.prompt")
public class PromptProperties {

    /** 是否启用 Prompt 增强（总开关） */
    private Boolean enabled = true;

    /** 是否在日志中打印完整 Prompt（调试用） */
    private Boolean debugLog = false;

    // ===== 1. 角色限定 =====
    /** 系统角色定义（如：Java后端工程师、业务面试官） */
    private String systemRole = "";

    /** 角色具体描述 */
    private String roleDescription = "";

    /** 角色专业领域 */
    private String roleDomain = "";

    // ===== 2. 格式约束 =====
    /** 输出格式：text/json/table/bullet/strict（严格模式只输出结果） */
    private String outputFormat = "text";

    /** JSON输出时的字段约束 */
    private Map<String, String> jsonSchema = new HashMap<>();

    /** 是否禁止多余话术（strict模式） */
    private Boolean strictOutput = false;

    /** 表格输出时的列定义 */
    private String tableColumns = "";

    // ===== 3. CoT 思维链 =====
    /** 是否启用CoT思维链推理 */
    private Boolean cotEnabled = false;

    /** CoT推理步骤提示词 */
    private String cotInstruction = "请一步步思考，先分析问题，再逐步推理，最后给出答案。";

    /** 自定义CoT推理步骤 */
    private List<String> cotSteps = new ArrayList<>();

    // ===== 4. Few-shot 少样本 =====
    /** 是否启用Few-shot少样本学习 */
    private Boolean fewShotEnabled = false;

    /** Few-shot示例列表（问题和答案对） */
    private List<FewShotExample> fewShotExamples = new ArrayList<>();

    // ===== 5. 边界约束 =====
    /** 是否启用边界约束 */
    private Boolean boundaryEnabled = true;

    /** 无数据时的回复话术 */
    private String noDataReply = "暂无相关数据，无法回答此问题。";

    /** 是否禁止编造数据 */
    private Boolean noFabrication = true;

    /** 额外禁止的行为描述 */
    private String additionalConstraints = "";

    // ===== 6. 上下文约束 =====
    /** 上下文窗口大小限制（字符数） */
    private Integer contextWindowSize = 4000;

    /** 是否在Prompt末尾添加"仅基于上文回答"的约束 */
    private Boolean contextOnlyReply = true;

    /** 上下文前缀标识 */
    private String contextPrefix = "【参考文档】";

    /** 系统指令前缀 */
    private String systemPrefix = "【系统指令】";

    /** 用户问题前缀 */
    private String userPrefix = "【用户问题】";

    // ===== 预设模板 =====
    /** 预设模板名称，选择后覆盖以上各字段 */
    private String preset = "";

    /**
     * Few-shot示例
     */
    @Data
    public static class FewShotExample {
        /** 示例问题 */
        private String question;

        /** 示例答案 */
        private String answer;

        /** 示例推理过程（可选，用于CoT） */
        private String reasoning;
    }
}
