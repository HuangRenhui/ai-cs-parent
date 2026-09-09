package com.ai.cs.base.controller;

import com.ai.cs.base.entity.ChatMsg;
import com.ai.cs.base.entity.ChatSession;
import com.ai.cs.base.service.ChatSessionService;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.List;

/**
 * 聊天会话控制器（供会话服务/AI 服务通过 Feign 调用）
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    @Resource
    private ChatSessionService chatSessionService;

    /**
     * 会话列表（按开始时间倒序）
     */
    @GetMapping("/list")
    public Result<List<ChatSession>> list() {
        return Result.success(chatSessionService.listSessions());
    }

    /**
     * 确保会话存在（幂等，不存在则创建）
     */
    @PostMapping("/ensure")
    public Result<String> ensure(@RequestBody SessionDTO dto) {
        chatSessionService.ensureSession(dto);
        return Result.success("会话已就绪");
    }

    /**
     * 保存一条聊天消息
     */
    @PostMapping("/message")
    public Result<String> saveMessage(@RequestBody SessionDTO dto) {
        chatSessionService.saveMessage(dto);
        return Result.success("消息已保存");
    }

    /**
     * 查询会话的消息记录
     */
    @GetMapping("/{sessionId}/messages")
    public Result<List<ChatMsg>> messages(@PathVariable String sessionId) {
        return Result.success(chatSessionService.listMessages(sessionId));
    }

    /**
     * 结束会话
     */
    @PutMapping("/{sessionId}/end")
    public Result<String> end(@PathVariable String sessionId) {
        chatSessionService.endSession(sessionId);
        return Result.success("会话已结束");
    }

    /**
     * 标记会话转人工（仅改类型，不分配坐席）
     */
    @PutMapping("/{sessionId}/transfer")
    public Result<String> transfer(@PathVariable String sessionId) {
        chatSessionService.markTransferred(sessionId);
        return Result.success("已标记转人工");
    }
}
