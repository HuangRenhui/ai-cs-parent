package com.ai.cs.base.service;

import com.ai.cs.base.entity.ChatMsg;
import com.ai.cs.base.entity.ChatSession;
import com.ai.cs.base.mapper.ChatMsgMapper;
import com.ai.cs.base.mapper.ChatSessionMapper;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.enums.MsgTypeEnum;
import com.ai.cs.common.enums.SessionStatusEnum;
import com.ai.cs.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天会话与消息业务逻辑
 *
 * @author huangrenhui
 */
@Service
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    @Resource
    private ChatMsgMapper chatMsgMapper;

    /**
     * 确保会话存在：已存在则直接返回，不存在则按入参创建一条进行中的会话
     *
     * @param dto 会话入参（sessionId 必填）
     * @return 已存在或新建的会话
     */
    public ChatSession ensureSession(SessionDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSessionId())) {
            throw new BusinessException("会话ID不能为空");
        }
        // 幂等处理：同一 sessionId 重复进入直接复用旧会话
        ChatSession existing = this.getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, dto.getSessionId())
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        ChatSession session = new ChatSession();
        session.setSessionId(dto.getSessionId());
        // 未登录访客没有客户ID，用 0 占位
        session.setCustomerId(dto.getCustomerId() == null ? 0L : dto.getCustomerId());
        session.setAgentId(dto.getAgentId());
        // 默认 AI 会话
        session.setSessionType(dto.getSessionType() == null ? 1 : dto.getSessionType());
        session.setSessionStatus(SessionStatusEnum.ONGOING.getCode());
        session.setStartTime(LocalDateTime.now());
        this.save(session);
        return session;
    }

    /**
     * 保存一条聊天消息（消息落库前先确保会话存在）
     */
    public void saveMessage(SessionDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSessionId()) || !StringUtils.hasText(dto.getMsgContent())) {
            throw new BusinessException("会话ID和消息内容不能为空");
        }
        // 消息依附于会话，先兜底建会话
        ensureSession(dto);
        ChatMsg msg = new ChatMsg();
        msg.setSessionId(dto.getSessionId());
        msg.setMsgContent(dto.getMsgContent());
        // 未指明发送方时按用户消息处理
        msg.setMsgType(dto.getSenderType() == null ? MsgTypeEnum.USER.getCode() : dto.getSenderType());
        chatMsgMapper.insert(msg);
    }

    /**
     * 查询全部会话（按开始时间倒序，最新的在前）
     */
    public List<ChatSession> listSessions() {
        return this.list(new LambdaQueryWrapper<ChatSession>().orderByDesc(ChatSession::getStartTime));
    }

    /**
     * 查询某会话的消息记录（按时间正序，还原对话顺序）
     */
    public List<ChatMsg> listMessages(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException("会话ID不能为空");
        }
        return chatMsgMapper.selectList(new LambdaQueryWrapper<ChatMsg>()
                .eq(ChatMsg::getSessionId, sessionId)
                .orderByAsc(ChatMsg::getCreateTime));
    }

    /**
     * 结束会话：状态置为已结束并记录结束时间
     */
    public void endSession(String sessionId) {
        ChatSession session = this.getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        session.setSessionStatus(SessionStatusEnum.ENDED.getCode());
        session.setEndTime(LocalDateTime.now());
        this.updateById(session);
    }

    /** 转人工：会话类型改为人工，状态保持进行中。不分配坐席（排队仍缺）。 */
    public void markTransferred(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException("会话ID不能为空");
        }
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(sessionId);
        ChatSession session = ensureSession(dto);
        session.setSessionType(2);
        session.setSessionStatus(SessionStatusEnum.ONGOING.getCode());
        this.updateById(session);
    }
}
