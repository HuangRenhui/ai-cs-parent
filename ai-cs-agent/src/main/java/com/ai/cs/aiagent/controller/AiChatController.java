package com.ai.cs.aiagent.controller;

import com.ai.cs.aiagent.service.AgentToolService;
import com.ai.cs.aiagent.service.AiAgentService;
import com.ai.cs.aiagent.service.MultimodalChatService;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

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

    /** 文本对话 */
    @PostMapping("/chat/send")
    public Result<String> chat(@RequestBody ChatDTO dto) {
        String reply = aiAgentService.chat(dto);
        return Result.success(reply);
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
}
