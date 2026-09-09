package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.config.PromptProperties;
import com.ai.cs.knowledge.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Prompt 调试控制器
 * <p>
 * 提供运行时 Prompt 调试 API：
 * - 查看/切换预设模板
 * - 预览生成的 System Prompt 和 User Prompt
 * - 动态修改 Prompt 配置
 *
 * @author huangrenhui
 * @date 2026/7/27
 */
@Slf4j
@RestController
@RequestMapping("/api/prompt")
@RequiredArgsConstructor
public class PromptDebugController {

    private final PromptTemplateService promptTemplateService;
    private final PromptProperties promptProperties;

    /**
     * 获取当前 Prompt 配置
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", promptProperties.getEnabled());
        result.put("debugLog", promptProperties.getDebugLog());
        result.put("preset", promptProperties.getPreset());

        Map<String, Object> role = new LinkedHashMap<>();
        role.put("systemRole", promptProperties.getSystemRole());
        role.put("roleDescription", promptProperties.getRoleDescription());
        role.put("roleDomain", promptProperties.getRoleDomain());
        result.put("roleConfig", role);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("outputFormat", promptProperties.getOutputFormat());
        format.put("strictOutput", promptProperties.getStrictOutput());
        format.put("tableColumns", promptProperties.getTableColumns());
        format.put("jsonSchema", promptProperties.getJsonSchema());
        result.put("formatConfig", format);

        Map<String, Object> cot = new LinkedHashMap<>();
        cot.put("cotEnabled", promptProperties.getCotEnabled());
        cot.put("cotInstruction", promptProperties.getCotInstruction());
        cot.put("cotSteps", promptProperties.getCotSteps());
        result.put("cotConfig", cot);

        Map<String, Object> fewShot = new LinkedHashMap<>();
        fewShot.put("fewShotEnabled", promptProperties.getFewShotEnabled());
        fewShot.put("fewShotExamples", promptProperties.getFewShotExamples());
        result.put("fewShotConfig", fewShot);

        Map<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("boundaryEnabled", promptProperties.getBoundaryEnabled());
        boundary.put("noDataReply", promptProperties.getNoDataReply());
        boundary.put("noFabrication", promptProperties.getNoFabrication());
        boundary.put("additionalConstraints", promptProperties.getAdditionalConstraints());
        result.put("boundaryConfig", boundary);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("contextWindowSize", promptProperties.getContextWindowSize());
        context.put("contextOnlyReply", promptProperties.getContextOnlyReply());
        context.put("contextPrefix", promptProperties.getContextPrefix());
        result.put("contextConfig", context);

        return Result.success(result);
    }

    /**
     * 获取所有可用预设模板列表
     */
    @GetMapping("/presets")
    public Result<Map<String, Object>> listPresets() {
        List<String> names = promptTemplateService.getAvailablePresets();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("availablePresets", names);
        result.put("currentPreset", promptProperties.getPreset());
        return Result.success(result);
    }

    /**
     * 获取指定预设模板详情
     */
    @GetMapping("/presets/{name}")
    public Result<Map<String, Object>> getPresetDetail(@PathVariable String name) {
        PromptProperties preset = promptTemplateService.getPreset(name);
        if (preset == null) {
            return Result.fail(404, "预设模板不存在: " + name);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("systemRole", preset.getSystemRole());
        result.put("roleDescription", preset.getRoleDescription());
        result.put("roleDomain", preset.getRoleDomain());
        result.put("outputFormat", preset.getOutputFormat());
        result.put("strictOutput", preset.getStrictOutput());
        result.put("cotEnabled", preset.getCotEnabled());
        result.put("cotInstruction", preset.getCotInstruction());
        result.put("cotSteps", preset.getCotSteps());
        result.put("fewShotEnabled", preset.getFewShotEnabled());
        result.put("fewShotExamples", preset.getFewShotExamples());
        result.put("boundaryEnabled", preset.getBoundaryEnabled());
        result.put("noDataReply", preset.getNoDataReply());
        result.put("noFabrication", preset.getNoFabrication());
        result.put("contextOnlyReply", preset.getContextOnlyReply());
        return Result.success(result);
    }

    /**
     * 预览生成的 System Prompt
     */
    @GetMapping("/preview/system")
    public Result<Map<String, Object>> previewSystemPrompt() {
        String systemPrompt = promptTemplateService.buildSystemPrompt();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemPrompt", systemPrompt);
        result.put("length", systemPrompt.length());
        return Result.success(result);
    }

    /**
     * 预览生成的完整 Prompt
     */
    @PostMapping("/preview/full")
    public Result<Map<String, Object>> previewFullPrompt(@RequestBody Map<String, Object> request) {
        String question = (String) request.getOrDefault("question", "测试问题");
        @SuppressWarnings("unchecked")
        List<String> contextDocs = (List<String>) request.getOrDefault("contextDocs",
                List.of("这是第一条参考文档内容，包含了相关的业务信息。",
                        "这是第二条参考文档内容，补充了更多的技术细节。"));

        String systemPrompt = promptTemplateService.buildSystemPrompt();
        String userPrompt = promptTemplateService.buildUserPrompt(question, contextDocs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemPrompt", systemPrompt);
        result.put("userPrompt", userPrompt);
        result.put("fullPrompt", systemPrompt + "\n\n---\n\n" + userPrompt);
        result.put("totalLength", systemPrompt.length() + userPrompt.length());
        result.put("contextDocCount", contextDocs.size());
        return Result.success(result);
    }

    /**
     * 预览 messages 格式（OpenAI兼容）
     */
    @PostMapping("/preview/messages")
    public Result<Map<String, Object>> previewMessages(@RequestBody Map<String, Object> request) {
        String question = (String) request.getOrDefault("question", "测试问题");
        @SuppressWarnings("unchecked")
        List<String> contextDocs = (List<String>) request.getOrDefault("contextDocs",
                List.of("参考文档内容示例1", "参考文档内容示例2"));

        List<Map<String, Object>> messages = promptTemplateService.buildMessages(question, contextDocs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messages", messages);
        result.put("messageCount", messages.size());
        int totalLength = messages.stream()
                .mapToInt(m -> ((String) m.get("content")).length())
                .sum();
        result.put("totalLength", totalLength);
        return Result.success(result);
    }

    /**
     * 动态更新 Prompt 配置（运行时生效，不持久化）
     */
    @PostMapping("/config")
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (config.containsKey("enabled")) {
            promptProperties.setEnabled((Boolean) config.get("enabled"));
            result.put("enabled", "updated");
        }
        if (config.containsKey("debugLog")) {
            promptProperties.setDebugLog((Boolean) config.get("debugLog"));
            result.put("debugLog", "updated");
        }
        if (config.containsKey("preset")) {
            promptProperties.setPreset((String) config.get("preset"));
            result.put("preset", "updated to " + config.get("preset"));
        }
        // 角色限定
        if (config.containsKey("systemRole")) {
            promptProperties.setSystemRole((String) config.get("systemRole"));
            result.put("systemRole", "updated");
        }
        if (config.containsKey("roleDescription")) {
            promptProperties.setRoleDescription((String) config.get("roleDescription"));
            result.put("roleDescription", "updated");
        }
        if (config.containsKey("roleDomain")) {
            promptProperties.setRoleDomain((String) config.get("roleDomain"));
            result.put("roleDomain", "updated");
        }
        // 格式约束
        if (config.containsKey("outputFormat")) {
            promptProperties.setOutputFormat((String) config.get("outputFormat"));
            result.put("outputFormat", "updated");
        }
        if (config.containsKey("strictOutput")) {
            promptProperties.setStrictOutput((Boolean) config.get("strictOutput"));
            result.put("strictOutput", "updated");
        }
        // CoT
        if (config.containsKey("cotEnabled")) {
            promptProperties.setCotEnabled((Boolean) config.get("cotEnabled"));
            result.put("cotEnabled", "updated");
        }
        if (config.containsKey("cotInstruction")) {
            promptProperties.setCotInstruction((String) config.get("cotInstruction"));
            result.put("cotInstruction", "updated");
        }
        // Few-shot
        if (config.containsKey("fewShotEnabled")) {
            promptProperties.setFewShotEnabled((Boolean) config.get("fewShotEnabled"));
            result.put("fewShotEnabled", "updated");
        }
        // 边界约束
        if (config.containsKey("boundaryEnabled")) {
            promptProperties.setBoundaryEnabled((Boolean) config.get("boundaryEnabled"));
            result.put("boundaryEnabled", "updated");
        }
        if (config.containsKey("noDataReply")) {
            promptProperties.setNoDataReply((String) config.get("noDataReply"));
            result.put("noDataReply", "updated");
        }
        if (config.containsKey("noFabrication")) {
            promptProperties.setNoFabrication((Boolean) config.get("noFabrication"));
            result.put("noFabrication", "updated");
        }
        if (config.containsKey("additionalConstraints")) {
            promptProperties.setAdditionalConstraints((String) config.get("additionalConstraints"));
            result.put("additionalConstraints", "updated");
        }
        // 上下文约束
        if (config.containsKey("contextWindowSize")) {
            promptProperties.setContextWindowSize((Integer) config.get("contextWindowSize"));
            result.put("contextWindowSize", "updated");
        }
        if (config.containsKey("contextOnlyReply")) {
            promptProperties.setContextOnlyReply((Boolean) config.get("contextOnlyReply"));
            result.put("contextOnlyReply", "updated");
        }

        result.put("status", "success");
        result.put("message", "配置已动态更新（重启后恢复为 application.yml 中的值）");
        return Result.success(result);
    }

    /**
     * 重置 Prompt 配置（取消预设，恢复为 application.yml 默认值）
     */
    @PostMapping("/reset")
    public Result<Map<String, Object>> resetConfig() {
        promptProperties.setPreset("");
        return Result.success(Map.of("status", "success", "message", "已取消预设模板，恢复为默认配置"));
    }
}