package com.ai.cs.aiagent.controller;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:56
 * @description TODO
 */

import com.ai.cs.aiagent.service.AiAgentService;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiAgentService aiAgentService;

    @PostMapping("/chat/send")
    public Result<String> chat(@RequestBody ChatDTO dto) {
        String reply = aiAgentService.chat(dto);
        return Result.success(reply);
    }
}