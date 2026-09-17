package com.ai.cs.aiagent.controller;

import com.ai.cs.aiagent.service.*;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.ChatReplyDTO;
import com.ai.cs.common.llm.TenantQuotaService;
import com.ai.cs.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 聊天控制器
 * 支持文本对话、多模态对话、工具调用等功能
 *
 * @author huangrenhui
 * @date 2026/6/11 17:56
 */
@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiAgentService aiAgentService;

    @Resource
    private MultimodalChatService multimodalChatService;

    @Resource
    private AgentToolService agentToolService;

    @Resource
    private AgentAssistService agentAssistService;

    @Resource
    private StreamingChatService streamingChatService;

    @Resource
    private FlowOrchestrationService flowOrchestrationService;

    @Resource
    private IndustryPromptPackService industryPromptPackService;

    @Resource
    private TenantQuotaService tenantQuotaService;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 文本对话 */
    @PostMapping("/chat/send")
    public Result<ChatReplyDTO> chat(@Valid @RequestBody ChatDTO dto) {
        return Result.success(aiAgentService.chatDetail(dto));
    }

    /** 图片对话（多模态） */
    @PostMapping("/chat/image")
    public Result<String> chatWithImage(@RequestBody Map<String, String> params) {
        String imageUrl = params.get("imageUrl");
        String question = params.get("question");
        String reply = multimodalChatService.chatWithImage(imageUrl, question);
        return Result.success(reply);
    }

    /** 语音转文字 */
    @PostMapping("/chat/speech-to-text")
    public Result<String> speechToText(@RequestBody Map<String, String> params) {
        String audioUrl = params.get("audioUrl");
        String text = multimodalChatService.speechToText(audioUrl);
        return Result.success(text);
    }

    /** 文字转语音 */
    @PostMapping("/chat/text-to-speech")
    public Result<String> textToSpeech(@RequestBody Map<String, String> params) {
        String text = params.get("text");
        String audioUrl = multimodalChatService.textToSpeech(text);
        return Result.success(audioUrl);
    }

    /** 获取可用工具列表 */
    @GetMapping("/tools/list")
    public Result<List<Map<String, String>>> toolsList() {
        return Result.success(agentToolService.getAvailableTools());
    }

    /** 执行工具链 */
    @PostMapping("/tools/chain")
    public Result<String> toolChain(@RequestBody Map<String, Object> params) {
        String sessionId = (String) params.get("sessionId");
        String userMsg = (String) params.get("userMsg");
        @SuppressWarnings("unchecked")
        List<String> toolNames = (List<String>) params.get("tools");
        String result = agentToolService.executeToolChain(sessionId, userMsg, toolNames);
        return Result.success(result);
    }

    /** 清除工具缓存 */
    @PostMapping("/tools/cache/clear")
    public Result<String> clearToolCache() {
        agentToolService.clearCache();
        return Result.success("缓存已清除");
    }

    /** 坐席辅助-推荐回复 */
    @PostMapping("/assist/recommend")
    public Result<List<String>> recommendReplies(@RequestBody Map<String, String> params) {
        String history = params.get("history");
        String userMsg = params.get("userMsg");
        return Result.success(agentAssistService.recommendReplies(history, userMsg));
    }

    /** 坐席辅助-会话摘要 */
    @PostMapping("/assist/summary")
    public Result<String> summarizeSession(@RequestBody Map<String, String> params) {
        String history = params.get("history");
        return Result.success(agentAssistService.summarizeSession(history));
    }

    /** 坐席辅助-下一句建议 */
    @PostMapping("/assist/next-sentence")
    public Result<String> suggestNextSentence(@RequestBody Map<String, String> params) {
        String history = params.get("history");
        String userMsg = params.get("userMsg");
        return Result.success(agentAssistService.suggestNextSentence(history, userMsg));
    }

    /** 流式对话（SSE） */
    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@RequestBody ChatDTO dto) {
        SseEmitter emitter = new SseEmitter(120_000L);
        emitters.put(dto.getSessionId(), emitter);
        emitter.onCompletion(() -> emitters.remove(dto.getSessionId()));
        emitter.onTimeout(() -> emitters.remove(dto.getSessionId()));
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", dto.getMsg()));
        streamingChatService.streamChat(dto.getSessionId(), messages, emitter);
        return emitter;
    }

    /** 打断流式输出 */
    @PostMapping("/chat/interrupt")
    public Result<String> interruptStream(@RequestBody Map<String, String> params) {
        String sessionId = params.get("sessionId");
        boolean success = streamingChatService.interrupt(sessionId);
        return success ? Result.success("已打断") : Result.fail(400, "打断失败，可能已输出完成");
    }

    /** 流程编排-执行决策树步骤 */
    @PostMapping("/flow/step")
    public Result<FlowOrchestrationService.FlowStepResult> executeFlowStep(@RequestBody Map<String, String> params) {
        String flowId = params.get("flowId");
        String sessionId = params.get("sessionId");
        String userMsg = params.get("userMsg");
        return Result.success(flowOrchestrationService.executeStep(flowId, sessionId, userMsg));
    }

    /** 获取行业提示词包 */
    @GetMapping("/industry-pack/{packCode}")
    public Result<IndustryPromptPackService.PromptPack> getIndustryPack(@PathVariable String packCode) {
        IndustryPromptPackService.PromptPack pack = industryPromptPackService.getPack(packCode);
        return pack != null ? Result.success(pack) : Result.fail(404, "行业包不存在: " + packCode);
    }

    /** 租户配额-查询当前用量 */
    @GetMapping("/quota/{tenantCode}")
    public Result<Map<String, Object>> getTenantQuota(@PathVariable String tenantCode) {
        long usage = tenantQuotaService.getCurrentUsage(tenantCode);
        long limit = tenantQuotaService.getQuotaLimit(tenantCode);
        Map<String, Object> data = Map.of(
                "usage", usage,
                "limit", limit,
                "exceeded", tenantQuotaService.isQuotaExceeded(tenantCode)
        );
        return Result.success(data);
    }

    /** 租户配额-设置上限 */
    @PostMapping("/quota/{tenantCode}")
    public Result<String> setTenantQuota(@PathVariable String tenantCode, @RequestBody Map<String, Object> params) {
        long limit = Long.parseLong(String.valueOf(params.getOrDefault("limit", "0")));
        tenantQuotaService.setQuota(tenantCode, limit);
        return Result.success("配额已设置");
    }
}