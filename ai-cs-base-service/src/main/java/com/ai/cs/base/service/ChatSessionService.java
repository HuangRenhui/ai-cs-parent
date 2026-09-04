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

@Service
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    @Resource
    private ChatMsgMapper chatMsgMapper;

    public ChatSession ensureSession(SessionDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSessionId())) {
            throw new BusinessException("会话ID不能为空");
        }
        ChatSession existing = this.getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, dto.getSessionId())
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        ChatSession session = new ChatSession();
        session.setSessionId(dto.getSessionId());
        session.setCustomerId(dto.getCustomerId() == null ? 0L : dto.getCustomerId());
        session.setAgentId(dto.getAgentId());
        session.setSessionType(dto.getSessionType() == null ? 1 : dto.getSessionType());
        session.setSessionStatus(SessionStatusEnum.ONGOING.getCode());
        session.setStartTime(LocalDateTime.now());
        this.save(session);
        return session;
    }

    public void saveMessage(SessionDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSessionId()) || !StringUtils.hasText(dto.getMsgContent())) {
            throw new BusinessException("会话ID和消息内容不能为空");
        }
        ensureSession(dto);
        ChatMsg msg = new ChatMsg();
        msg.setSessionId(dto.getSessionId());
        msg.setMsgContent(dto.getMsgContent());
        msg.setMsgType(dto.getSenderType() == null ? MsgTypeEnum.USER.getCode() : dto.getSenderType());
        chatMsgMapper.insert(msg);
    }

    public List<ChatSession> listSessions() {
        return this.list(new LambdaQueryWrapper<ChatSession>().orderByDesc(ChatSession::getStartTime));
    }

    public List<ChatMsg> listMessages(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException("会话ID不能为空");
        }
        return chatMsgMapper.selectList(new LambdaQueryWrapper<ChatMsg>()
                .eq(ChatMsg::getSessionId, sessionId)
                .orderByAsc(ChatMsg::getCreateTime));
    }

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
