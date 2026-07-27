package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.PromptProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Prompt 模板构建服务
 * <p>
 * 解决六大 Prompt 调试问题：
 * 1. 角色限定：指定身份（Java后端工程师、业务面试官）
 * 2. 格式约束：指定JSON/表格/要点返回，禁止多余话术
 * 3. CoT思维链：让模型分步思考、逐步推理
 * 4. Few-shot少样本：附带1-3个标准答案示例
 * 5. 边界约束：禁止编造数据，不知道直接回复暂无数据
 * 6. 上下文约束：精简冗余上下文，避免窗口溢出
 * <p>
 * 用法：注入此服务后，调用 buildSystemPrompt() + buildUserPrompt(question, context)
 * 拼成完整 messages 数组发送给 LLM。
 *
 * @author huangrenhui
 * @date 2026/7/27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptProperties promptProperties;

    // ==================== 预设模板 ====================

    /**
     * 预设模板注册表
     */
    private static final Map<String, PromptProperties> PRESETS = new LinkedHashMap<>();

    static {
        // --- 预设1：技术问答助手 ---
        PromptProperties tech = new PromptProperties();
        tech.setSystemRole("Java后端工程师");
        tech.setRoleDescription("你是一名经验丰富的Java后端工程师，精通Spring Boot、微服务架构、数据库设计和系统优化。");
        tech.setRoleDomain("Java后端开发、Spring生态、微服务、分布式系统");
        tech.setOutputFormat("bullet");
        tech.setStrictOutput(false);
        tech.setCotEnabled(false);
        tech.setBoundaryEnabled(true);
        tech.setNoDataReply("暂无相关技术资料，建议查阅官方文档。");
        tech.setContextOnlyReply(true);
        PRESETS.put("java-engineer", tech);

        // --- 预设2：业务面试官 ---
        PromptProperties interviewer = new PromptProperties();
        interviewer.setSystemRole("业务面试官");
        interviewer.setRoleDescription("你是一位资深的业务面试官，善于通过提问评估候选人的技术能力和项目经验。");
        interviewer.setRoleDomain("技术面试、人才评估");
        interviewer.setOutputFormat("text");
        interviewer.setStrictOutput(false);
        interviewer.setCotEnabled(false);
        interviewer.setBoundaryEnabled(true);
        interviewer.setNoDataReply("暂无该候选人的相关信息。");
        PRESETS.put("interviewer", interviewer);

        // --- 预设3：JSON结构化输出 ---
        PromptProperties jsonOutput = new PromptProperties();
        jsonOutput.setSystemRole("数据分析助手");
        jsonOutput.setRoleDescription("你是一个数据分析助手，所有回答必须以标准JSON格式返回。");
        jsonOutput.setOutputFormat("json");
        jsonOutput.setStrictOutput(true);
        jsonOutput.setBoundaryEnabled(true);
        jsonOutput.setNoDataReply("{\"status\": \"no_data\", \"message\": \"暂无相关数据\"}");
        jsonOutput.setContextOnlyReply(true);
        PRESETS.put("json-output", jsonOutput);

        // --- 预设4：CoT 数学/逻辑推理 ---
        PromptProperties cot = new PromptProperties();
        cot.setSystemRole("逻辑推理专家");
        cot.setRoleDescription("你是一个逻辑推理专家，擅长逐步分析问题并给出准确答案。");
        cot.setOutputFormat("text");
        cot.setCotEnabled(true);
        cot.setCotInstruction("请按以下步骤推理：\n1. 理解问题的核心要点\n2. 列出已知条件\n3. 逐步推导\n4. 给出最终答案");
        cot.setCotSteps(Arrays.asList("理解问题", "列出已知条件", "逐步推导", "给出答案"));
        cot.setBoundaryEnabled(true);
        cot.setNoDataReply("信息不足，无法完成推理。");
        PRESETS.put("cot-reasoning", cot);

        // --- 预设5：Few-shot 示例学习 ---
        PromptProperties fewShot = new PromptProperties();
        fewShot.setSystemRole("智能客服助手");
        fewShot.setRoleDescription("你是一个专业的智能客服，回答风格简洁准确。");
        fewShot.setOutputFormat("text");
        fewShot.setFewShotEnabled(true);
        List<PromptProperties.FewShotExample> examples = new ArrayList<>();
        PromptProperties.FewShotExample e1 = new PromptProperties.FewShotExample();
        e1.setQuestion("如何重置密码？");
        e1.setAnswer("请前往设置-安全中心-修改密码，按提示输入原密码和新密码即可完成重置。");
        examples.add(e1);
        PromptProperties.FewShotExample e2 = new PromptProperties.FewShotExample();
        e2.setQuestion("支持哪些支付方式？");
        e2.setAnswer("目前支持微信支付、支付宝和银行卡支付三种方式。");
        examples.add(e2);
        fewShot.setFewShotExamples(examples);
        fewShot.setBoundaryEnabled(true);
        fewShot.setNoDataReply("暂无相关信息，建议联系人工客服。");
        PRESETS.put("few-shot", fewShot);

        // --- 预设6：严格边界约束（防幻觉） ---
        PromptProperties strict = new PromptProperties();
        strict.setSystemRole("知识库问答助手");
        strict.setRoleDescription("你只能基于提供的参考文档回答问题，绝对不能编造任何信息。");
        strict.setOutputFormat("text");
        strict.setStrictOutput(true);
        strict.setBoundaryEnabled(true);
        strict.setNoFabrication(true);
        strict.setNoDataReply("暂无相关资料");
        strict.setContextOnlyReply(true);
        strict.setAdditionalConstraints("禁止使用\"根据我的经验\"、\"通常来说\"等模糊表述。如果文档中没有明确说明，必须回复\"暂无相关资料\"。");
        PRESETS.put("strict-boundary", presetDefaults(strict));

        // --- 预设7：表格输出 ---
        PromptProperties table = new PromptProperties();
        table.setSystemRole("数据分析师");
        table.setRoleDescription("你是一个数据分析师，善于将信息整理为结构化表格。");
        table.setOutputFormat("table");
        table.setTableColumns("序号,名称,说明,备注");
        table.setStrictOutput(false);
        table.setBoundaryEnabled(true);
        table.setNoDataReply("暂无数据可展示。");
        PRESETS.put("table-output", table);

        // --- 预设8：全功能组合（角色+格式+CoT+Few-shot+边界+上下文） ---
        PromptProperties full = new PromptProperties();
        full.setSystemRole("资深技术顾问");
        full.setRoleDescription("你是一位资深技术顾问，回答专业、准确、结构化。");
        full.setRoleDomain("软件工程、系统架构、数据库、云计算");
        full.setOutputFormat("bullet");
        full.setStrictOutput(true);
        full.setCotEnabled(true);
        full.setCotInstruction("请先分析问题本质，再列出关键要点，最后给出结构化答案。");
        full.setFewShotEnabled(true);
        List<PromptProperties.FewShotExample> fullExamples = new ArrayList<>();
        PromptProperties.FewShotExample fe1 = new PromptProperties.FewShotExample();
        fe1.setQuestion("微服务之间如何通信？");
        fe1.setAnswer("- 同步通信：REST API（HTTP/HTTPS）、gRPC\n- 异步通信：消息队列（Kafka/RabbitMQ/RocketMQ）\n- 推荐：查询场景用同步，写操作/事件驱动用异步");
        fe1.setReasoning("先分类（同步/异步），再列举具体技术，最后给出推荐场景。");
        fullExamples.add(fe1);
        full.setFewShotExamples(fullExamples);
        full.setBoundaryEnabled(true);
        full.setNoFabrication(true);
        full.setNoDataReply("暂无相关资料，无法给出准确建议。");
        full.setContextOnlyReply(true);
        PRESETS.put("full-combo", full);
    }

    private static PromptProperties presetDefaults(PromptProperties p) {
        return p; // 直接返回，已在上面设置
    }

    // ==================== 核心构建方法 ====================

    /**
     * 获取当前生效的配置（优先使用运行时覆盖，其次预设，最后默认）
     */
    public PromptProperties getEffectiveConfig() {
        String presetName = promptProperties.getPreset();
        if (presetName != null && !presetName.isEmpty() && PRESETS.containsKey(presetName)) {
            PromptProperties preset = PRESETS.get(presetName);
            // 合并：运行时配置覆盖预设
            return merge(preset, promptProperties);
        }
        return promptProperties;
    }

    /**
     * 构建 System Prompt（角色限定 + 格式约束 + 边界约束）
     * <p>
     * 这段内容作为 messages[0] 的 system 角色发送
     */
    public String buildSystemPrompt() {
        PromptProperties cfg = getEffectiveConfig();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // ===== 1. 角色限定 =====
        String role = getEffectiveRole(cfg);
        if (!role.isEmpty()) {
            sb.append(role).append("\n\n");
        }

        // ===== 2. 格式约束 =====
        String formatRule = buildFormatConstraint(cfg);
        if (!formatRule.isEmpty()) {
            sb.append(formatRule).append("\n\n");
        }

        // ===== 5. 边界约束（放在 System Prompt 中作为全局约束） =====
        String boundaryRule = buildBoundaryConstraint(cfg);
        if (!boundaryRule.isEmpty()) {
            sb.append(boundaryRule).append("\n\n");
        }

        String systemPrompt = sb.toString().trim();
        if (cfg.getDebugLog()) {
            log.info("========== System Prompt ==========\n{}", systemPrompt);
        }
        return systemPrompt;
    }

    /**
     * 构建 User Prompt（问题 + 上下文 + CoT + Few-shot）
     * <p>
     * 这段内容作为 messages[1] 的 user 角色发送
     *
     * @param question     用户问题
     * @param contextDocs  检索到的上下文文档列表
     * @return 完整的 User Prompt
     */
    public String buildUserPrompt(String question, List<String> contextDocs) {
        PromptProperties cfg = getEffectiveConfig();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            // 未启用增强时，使用简单拼接（兼容旧逻辑）
            return buildSimplePrompt(question, contextDocs);
        }

        StringBuilder sb = new StringBuilder();

        // ===== 6. 上下文约束（精简上下文，避免窗口溢出） =====
        String context = buildContextSection(question, contextDocs, cfg);
        if (!context.isEmpty()) {
            sb.append(context).append("\n\n");
        }

        // ===== 4. Few-shot 少样本 =====
        String fewShot = buildFewShotSection(cfg);
        if (!fewShot.isEmpty()) {
            sb.append(fewShot).append("\n\n");
        }

        // ===== 3. CoT 思维链（作为用户指令的一部分） =====
        String cot = buildCotSection(cfg);
        if (!cot.isEmpty()) {
            sb.append(cot).append("\n\n");
        }

        // ===== 用户问题 =====
        sb.append(cfg.getUserPrefix()).append("：").append(question).append("\n");
        sb.append("请回答：");

        String userPrompt = sb.toString();
        if (cfg.getDebugLog()) {
            log.info("========== User Prompt ==========\n{}", userPrompt);
        }
        return userPrompt;
    }

    /**
     * 构建完整 messages 数组（OpenAI 兼容格式）
     * <p>
     * 返回 List<Map>，直接用于 LlmClient 或 LangChain4j
     */
    public List<Map<String, Object>> buildMessages(String question, List<String> contextDocs) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System message
        String systemPrompt = buildSystemPrompt();
        if (!systemPrompt.isEmpty()) {
            Map<String, Object> sysMsg = new LinkedHashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        // User message
        String userPrompt = buildUserPrompt(question, contextDocs);
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        return messages;
    }

    /**
     * 构建完整 Prompt 字符串（兼容旧版 llmClient.call(prompt) 单字符串模式）
     * <p>
     * 将 System Prompt 和 User Prompt 合并为一个字符串
     */
    public String buildFullPrompt(String question, List<String> contextDocs) {
        StringBuilder sb = new StringBuilder();

        String systemPrompt = buildSystemPrompt();
        if (!systemPrompt.isEmpty()) {
            sb.append(systemPrompt).append("\n\n---\n\n");
        }

        sb.append(buildUserPrompt(question, contextDocs));
        return sb.toString();
    }

    // ==================== 各策略构建方法 ====================

    /**
     * 1. 角色限定：构建系统身份定义
     */
    private String getEffectiveRole(PromptProperties cfg) {
        StringBuilder role = new StringBuilder();

        if (cfg.getSystemRole() != null && !cfg.getSystemRole().isEmpty()) {
            role.append("你是").append(cfg.getSystemRole()).append("。");
        }

        if (cfg.getRoleDescription() != null && !cfg.getRoleDescription().isEmpty()) {
            if (role.length() > 0) role.append(" ");
            role.append(cfg.getRoleDescription());
        }

        if (cfg.getRoleDomain() != null && !cfg.getRoleDomain().isEmpty()) {
            role.append(" 你的专业领域包括：").append(cfg.getRoleDomain()).append("。");
        }

        return role.toString();
    }

    /**
     * 2. 格式约束：指定输出格式
     */
    private String buildFormatConstraint(PromptProperties cfg) {
        String format = cfg.getOutputFormat();
        if (format == null || "text".equals(format)) {
            return "";
        }

        StringBuilder rule = new StringBuilder();

        switch (format) {
            case "json" -> {
                rule.append("【输出格式要求】\n");
                rule.append("请严格按照JSON格式返回，不要包含任何其他内容。");
                Map<String, String> schema = cfg.getJsonSchema();
                if (schema != null && !schema.isEmpty()) {
                    rule.append(" JSON字段要求：\n");
                    schema.forEach((key, desc) ->
                            rule.append("- ").append(key).append(": ").append(desc).append("\n"));
                }
            }
            case "table" -> {
                rule.append("【输出格式要求】\n");
                rule.append("请以Markdown表格格式返回结果。");
                if (cfg.getTableColumns() != null && !cfg.getTableColumns().isEmpty()) {
                    rule.append(" 表格列：").append(cfg.getTableColumns()).append("。");
                }
            }
            case "bullet" -> {
                rule.append("【输出格式要求】\n");
                rule.append("请使用要点列表（bullet points）格式回答，每条要点以\"-\"开头，简明扼要。");
            }
            case "strict" -> {
                rule.append("【输出格式要求】\n");
                rule.append("只输出最终答案，禁止包含任何解释、说明、礼貌用语或补充信息。");
            }
        }

        // 严格输出模式追加
        if (Boolean.TRUE.equals(cfg.getStrictOutput()) && !"strict".equals(format)) {
            if (rule.length() > 0) rule.append(" ");
            rule.append("禁止多余话术，直接给出答案。");
        }

        return rule.toString();
    }

    /**
     * 3. CoT 思维链：分步推理指令
     */
    private String buildCotSection(PromptProperties cfg) {
        if (!Boolean.TRUE.equals(cfg.getCotEnabled())) {
            return "";
        }

        StringBuilder cot = new StringBuilder();
        cot.append("【推理要求 - 请逐步思考】\n");

        if (cfg.getCotInstruction() != null && !cfg.getCotInstruction().isEmpty()) {
            cot.append(cfg.getCotInstruction());
        }

        List<String> steps = cfg.getCotSteps();
        if (steps != null && !steps.isEmpty()) {
            cot.append("\n");
            for (int i = 0; i < steps.size(); i++) {
                cot.append("步骤").append(i + 1).append("：").append(steps.get(i)).append("\n");
            }
        }

        return cot.toString();
    }

    /**
     * 4. Few-shot 少样本：附带标准答案示例
     */
    private String buildFewShotSection(PromptProperties cfg) {
        if (!Boolean.TRUE.equals(cfg.getFewShotEnabled())) {
            return "";
        }

        List<PromptProperties.FewShotExample> examples = cfg.getFewShotExamples();
        if (examples == null || examples.isEmpty()) {
            return "";
        }

        StringBuilder fewShot = new StringBuilder();
        fewShot.append("【回答示例 - 请参照以下格式和风格回答】\n\n");

        for (int i = 0; i < Math.min(examples.size(), 3); i++) {
            PromptProperties.FewShotExample ex = examples.get(i);
            fewShot.append("示例").append(i + 1).append("：\n");
            fewShot.append("问题：").append(ex.getQuestion()).append("\n");
            if (ex.getReasoning() != null && !ex.getReasoning().isEmpty()) {
                fewShot.append("推理：").append(ex.getReasoning()).append("\n");
            }
            fewShot.append("答案：").append(ex.getAnswer()).append("\n\n");
        }

        fewShot.append("请严格按照以上示例的格式和风格回答用户问题。\n");
        return fewShot.toString();
    }

    /**
     * 5. 边界约束：禁止编造、无数据时的兜底回复
     */
    private String buildBoundaryConstraint(PromptProperties cfg) {
        if (!Boolean.TRUE.equals(cfg.getBoundaryEnabled())) {
            return "";
        }

        StringBuilder rule = new StringBuilder();
        rule.append("【回答边界约束】\n");

        // 禁止编造
        if (Boolean.TRUE.equals(cfg.getNoFabrication())) {
            rule.append("1. 仅基于提供的参考文档回答，绝对禁止编造任何数据、接口、编号或未在文档中出现的信息。\n");
        }

        // 无数据回复
        if (cfg.getNoDataReply() != null && !cfg.getNoDataReply().isEmpty()) {
            rule.append("2. 如果参考文档中没有相关信息，直接回复\"\").append(cfg.getNoDataReply()).append(\"\"，禁止猜测。\n");
        }

        // 额外约束
        if (cfg.getAdditionalConstraints() != null && !cfg.getAdditionalConstraints().isEmpty()) {
            rule.append("3. ").append(cfg.getAdditionalConstraints()).append("\n");
        }

        return rule.toString();
    }

    /**
     * 6. 上下文约束：精简上下文，避免窗口溢出
     */
    private String buildContextSection(String question, List<String> contextDocs, PromptProperties cfg) {
        if (contextDocs == null || contextDocs.isEmpty()) {
            if (Boolean.TRUE.equals(cfg.getContextOnlyReply())) {
                return cfg.getContextPrefix() + "：（无相关文档，请直接回复无数据）";
            }
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append(cfg.getContextPrefix()).append("：\n");

        int maxLength = cfg.getContextWindowSize();
        int currentLength = 0;

        for (int i = 0; i < contextDocs.size(); i++) {
            String doc = contextDocs.get(i);
            if (doc == null || doc.isBlank()) continue;

            String entry = (i + 1) + ". " + doc.trim() + "\n";

            // 上下文窗口限制
            if (currentLength + entry.length() > maxLength) {
                // 截断处理：保留前半部分
                int remaining = maxLength - currentLength - 20;
                if (remaining > 50) {
                    entry = (i + 1) + ". " + doc.trim().substring(0, remaining) + "...\n";
                } else {
                    context.append("（后续").append(contextDocs.size() - i).append("条文档因上下文窗口限制已省略）\n");
                    break;
                }
            }

            context.append(entry);
            currentLength += entry.length();
        }

        // 上下文仅回答约束
        if (Boolean.TRUE.equals(cfg.getContextOnlyReply())) {
            context.append("\n注意：请仅基于以上参考文档内容回答问题，不要引入外部知识。");
        }

        return context.toString();
    }

    // ==================== 简单模式（兼容旧逻辑） ====================

    /**
     * 简单 Prompt（不启用增强时使用，保持向后兼容）
     */
    private String buildSimplePrompt(String question, List<String> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("【参考真实业务文档】\n");
        if (docs != null) {
            docs.forEach(d -> sb.append(d).append("\n"));
        }
        sb.append("""
                
                严格遵守规则：
                1. 仅使用上面参考文档内容回答，禁止编造文档编号、接口、业务数据；
                2. 无相关信息直接回复「暂无相关资料」，禁止猜测。
                
                用户问题：""").append(question);
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    /**
     * 合并预设和运行时配置（运行时配置覆盖预设）
     * <p>
     * 重要：会创建预设的深拷贝，不会污染 PRESETS 静态 Map 中的原始预设对象。
     */
    private PromptProperties merge(PromptProperties preset, PromptProperties runtime) {
        // 创建预设的深拷贝，避免污染静态 PRESETS 中的原始对象
        PromptProperties merged = deepCopy(preset);

        // 运行时配置中非空/非默认的字段覆盖
        if (runtime.getSystemRole() != null && !runtime.getSystemRole().isEmpty()) {
            merged.setSystemRole(runtime.getSystemRole());
        }
        if (runtime.getRoleDescription() != null && !runtime.getRoleDescription().isEmpty()) {
            merged.setRoleDescription(runtime.getRoleDescription());
        }
        if (runtime.getRoleDomain() != null && !runtime.getRoleDomain().isEmpty()) {
            merged.setRoleDomain(runtime.getRoleDomain());
        }
        if (runtime.getOutputFormat() != null && !"text".equals(runtime.getOutputFormat())) {
            merged.setOutputFormat(runtime.getOutputFormat());
        }
        if (runtime.getStrictOutput() != null) {
            merged.setStrictOutput(runtime.getStrictOutput());
        }
        if (runtime.getCotEnabled() != null) {
            merged.setCotEnabled(runtime.getCotEnabled());
        }
        if (runtime.getCotInstruction() != null && !runtime.getCotInstruction().isEmpty()) {
            merged.setCotInstruction(runtime.getCotInstruction());
        }
        if (runtime.getFewShotEnabled() != null) {
            merged.setFewShotEnabled(runtime.getFewShotEnabled());
        }
        if (runtime.getFewShotExamples() != null && !runtime.getFewShotExamples().isEmpty()) {
            merged.setFewShotExamples(new ArrayList<>(runtime.getFewShotExamples()));
        }
        if (runtime.getNoDataReply() != null && !runtime.getNoDataReply().isEmpty()) {
            merged.setNoDataReply(runtime.getNoDataReply());
        }
        if (runtime.getAdditionalConstraints() != null && !runtime.getAdditionalConstraints().isEmpty()) {
            merged.setAdditionalConstraints(runtime.getAdditionalConstraints());
        }
        if (runtime.getContextOnlyReply() != null) {
            merged.setContextOnlyReply(runtime.getContextOnlyReply());
        }
        if (runtime.getDebugLog() != null) {
            merged.setDebugLog(runtime.getDebugLog());
        }
        return merged;
    }

    /**
     * 深拷贝 PromptProperties（用于 merge 时保护预设原始对象不被污染）
     */
    private PromptProperties deepCopy(PromptProperties source) {
        PromptProperties copy = new PromptProperties();
        copy.setEnabled(source.getEnabled());
        copy.setDebugLog(source.getDebugLog());
        copy.setSystemRole(source.getSystemRole());
        copy.setRoleDescription(source.getRoleDescription());
        copy.setRoleDomain(source.getRoleDomain());
        copy.setOutputFormat(source.getOutputFormat());
        if (source.getJsonSchema() != null) {
            copy.setJsonSchema(new HashMap<>(source.getJsonSchema()));
        }
        copy.setStrictOutput(source.getStrictOutput());
        copy.setTableColumns(source.getTableColumns());
        copy.setCotEnabled(source.getCotEnabled());
        copy.setCotInstruction(source.getCotInstruction());
        if (source.getCotSteps() != null) {
            copy.setCotSteps(new ArrayList<>(source.getCotSteps()));
        }
        copy.setFewShotEnabled(source.getFewShotEnabled());
        if (source.getFewShotExamples() != null) {
            List<PromptProperties.FewShotExample> copiedExamples = new ArrayList<>();
            for (PromptProperties.FewShotExample ex : source.getFewShotExamples()) {
                PromptProperties.FewShotExample copiedEx = new PromptProperties.FewShotExample();
                copiedEx.setQuestion(ex.getQuestion());
                copiedEx.setAnswer(ex.getAnswer());
                copiedEx.setReasoning(ex.getReasoning());
                copiedExamples.add(copiedEx);
            }
            copy.setFewShotExamples(copiedExamples);
        }
        copy.setBoundaryEnabled(source.getBoundaryEnabled());
        copy.setNoDataReply(source.getNoDataReply());
        copy.setNoFabrication(source.getNoFabrication());
        copy.setAdditionalConstraints(source.getAdditionalConstraints());
        copy.setContextWindowSize(source.getContextWindowSize());
        copy.setContextOnlyReply(source.getContextOnlyReply());
        copy.setContextPrefix(source.getContextPrefix());
        copy.setSystemPrefix(source.getSystemPrefix());
        copy.setUserPrefix(source.getUserPrefix());
        copy.setPreset(source.getPreset());
        return copy;
    }

    /**
     * 获取所有可用预设名称
     */
    public List<String> getAvailablePresets() {
        return new ArrayList<>(PRESETS.keySet());
    }

    /**
     * 获取预设详情
     */
    public PromptProperties getPreset(String name) {
        return PRESETS.get(name);
    }
}
