package com.ai.cs.base.controller;

import com.ai.cs.base.entity.ChatMsg;
import com.ai.cs.base.entity.ChatSession;
import com.ai.cs.base.service.ChatSessionService;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/session")
public class SessionController {

    @Resource
    private ChatSessionService chatSessionService;

    @GetMapping("/list")
    public Result<List<ChatSession>> list() {
        return Result.success(chatSessionService.listSessions());
    }

    @PostMapping("/ensure")
    public Result<String> ensure(@RequestBody SessionDTO dto) {
        chatSessionService.ensureSession(dto);
        return Result.success("会话已就绪");
    }

    @PostMapping("/message")
    public Result<String> saveMessage(@RequestBody SessionDTO dto) {
        chatSessionService.saveMessage(dto);
        return Result.success("消息已保存");
    }

    @GetMapping("/{sessionId}/messages")
    public Result<List<ChatMsg>> messages(@PathVariable String sessionId) {
        return Result.success(chatSessionService.listMessages(sessionId));
    }

    @PutMapping("/{sessionId}/end")
    public Result<String> end(@PathVariable String sessionId) {
        chatSessionService.endSession(sessionId);
        return Result.success("会话已结束");
    }

    @PutMapping("/{sessionId}/transfer")
    public Result<String> transfer(@PathVariable String sessionId) {
        chatSessionService.markTransferred(sessionId);
        return Result.success("已标记转人工");
    }
}
