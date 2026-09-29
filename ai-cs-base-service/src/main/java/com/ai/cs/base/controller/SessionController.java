package com.ai.cs.base.controller;

import com.ai.cs.base.entity.ChatMsg;
import com.ai.cs.base.entity.ChatSession;
import com.ai.cs.base.service.ChatSessionService;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.dto.SessionSnapshotDTO;
import com.ai.cs.common.dto.TransferResultDTO;
import com.ai.cs.common.result.PageResult;
import com.ai.cs.common.result.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
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
     * 会话分页列表（生产数据量下请优先用本接口）
     */
    @GetMapping("/page")
    public Result<PageResult<ChatSession>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) Integer sessionStatus,
                                                @RequestParam(required = false) Integer sessionType,
                                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(chatSessionService.pageSessions(pageNum, pageSize, sessionStatus, sessionType, startTime, endTime));
    }

    /**
     * 会话列表（按开始时间倒序；兼容旧前端，最多 200 条）
     */
    @GetMapping("/list")
    public Result<List<ChatSession>> list() {
        return Result.success(chatSessionService.listSessions());
    }

    /**
     * 按 sessionId 查询会话快照（WebSocket 握手鉴权用）
     */
    @GetMapping("/{sessionId}/snapshot")
    public Result<SessionSnapshotDTO> snapshot(@PathVariable String sessionId) {
        return Result.success(chatSessionService.toSnapshot(chatSessionService.getBySessionId(sessionId)));
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
     * 同一事务保存用户消息与回复
     */
    @PostMapping("/turn")
    public Result<String> saveTurn(@RequestBody List<SessionDTO> messages) {
        if (messages == null || messages.size() != 2) {
            return Result.fail(400, "需要用户消息与回复两条");
        }
        chatSessionService.saveTurn(messages.get(0), messages.get(1));
        return Result.success("对话已保存");
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
     * 标记会话转人工并分配在线坐席（无在线坐席时仅改类型），返回接入坐席工号与姓名
     */
    @PutMapping("/{sessionId}/transfer")
    public Result<TransferResultDTO> transfer(@PathVariable String sessionId) {
        return Result.success("已转接人工", chatSessionService.markTransferred(sessionId));
    }

    /**
     * 匿名访客登录后，把其历史会话的 customerId 从 0 更新为正式客户 ID。
     * 必须带 visitorRef，禁止按 customerId=0 全表合并。
     */
    @PostMapping("/merge-anonymous")
    public Result<Integer> mergeAnonymous(@RequestBody SessionDTO dto) {
        if (dto == null) {
            return Result.fail("参数不能为空");
        }
        return Result.success(chatSessionService.mergeAnonymousSessions(dto.getVisitorRef(), dto.getCustomerId()));
    }
}
