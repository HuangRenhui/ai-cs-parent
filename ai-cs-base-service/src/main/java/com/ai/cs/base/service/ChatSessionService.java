package com.ai.cs.base.service;

import com.ai.cs.base.entity.Agent;
import com.ai.cs.base.entity.ChatMsg;
import com.ai.cs.base.entity.ChatSession;
import com.ai.cs.base.mapper.ChatMsgMapper;
import com.ai.cs.base.mapper.ChatSessionMapper;
import com.ai.cs.base.support.OpenWebhookClient;
import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.common.dto.AttachmentDTO;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.dto.SessionSnapshotDTO;
import com.ai.cs.common.dto.TransferResultDTO;
import com.ai.cs.common.enums.AgentStatusEnum;
import com.ai.cs.common.enums.MsgTypeEnum;
import com.ai.cs.common.enums.SessionStatusEnum;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 聊天会话与消息业务逻辑
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    @Resource
    private ChatMsgMapper chatMsgMapper;
    @Resource
    private AgentService agentService;
    @Autowired(required = false)
    private OpenWebhookClient openWebhookClient;
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

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
            // 旧会话缺少 visitorRef 时补写，保证后续匿名合并能定位到本访客
            if (!StringUtils.hasText(existing.getVisitorRef()) && StringUtils.hasText(dto.getVisitorRef())) {
                existing.setVisitorRef(dto.getVisitorRef().trim());
                this.updateById(existing);
            }
            return existing;
        }
        ChatSession session = new ChatSession();
        session.setSessionId(dto.getSessionId());
        // 未登录访客没有客户ID，用 0 占位
        session.setCustomerId(dto.getCustomerId() == null ? 0L : dto.getCustomerId());
        // 记录访客标识，登录后才能精确合并本访客的匿名会话
        session.setVisitorRef(StringUtils.hasText(dto.getVisitorRef()) ? dto.getVisitorRef().trim() : null);
        session.setAgentId(dto.getAgentId());
        // 默认 AI 会话
        session.setSessionType(dto.getSessionType() == null ? 1 : dto.getSessionType());
        session.setSessionStatus(SessionStatusEnum.ONGOING.getCode());
        session.setStartTime(LocalDateTime.now());
        this.save(session);
        fireWebhook("session_start", session);
        return session;
    }

    /**
     * 按 sessionId 查询会话，不存在返回 null
     */
    public ChatSession getBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .last("limit 1"));
    }

    /**
     * 组装跨服务会话快照
     */
    public SessionSnapshotDTO toSnapshot(ChatSession session) {
        if (session == null) {
            return null;
        }
        SessionSnapshotDTO dto = new SessionSnapshotDTO();
        dto.setSessionId(session.getSessionId());
        dto.setCustomerId(session.getCustomerId());
        dto.setVisitorRef(session.getVisitorRef());
        dto.setAgentId(session.getAgentId());
        dto.setSessionType(session.getSessionType());
        dto.setSessionStatus(session.getSessionStatus());
        return dto;
    }

    /**
     * 保存一条聊天消息（消息落库前先确保会话存在）
     */
    public void saveMessage(SessionDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSessionId())) {
            throw new BusinessException("会话ID不能为空");
        }
        // 允许「纯附件消息」：文本与附件至少有一项即可
        boolean hasAttachment = dto.getAttachments() != null && !dto.getAttachments().isEmpty();
        if (!StringUtils.hasText(dto.getMsgContent()) && !hasAttachment) {
            throw new BusinessException("消息内容不能为空");
        }
        // 消息依附于会话，先兜底建会话
        ensureSession(dto);
        ChatMsg msg = new ChatMsg();
        msg.setSessionId(dto.getSessionId());
        msg.setMsgContent(StringUtils.hasText(dto.getMsgContent()) ? dto.getMsgContent() : "");
        // 未指明发送方时按用户消息处理
        msg.setMsgType(dto.getSenderType() == null ? MsgTypeEnum.USER.getCode() : dto.getSenderType());
        msg.setAttachments(toAttachmentMaps(dto.getAttachments()));
        chatMsgMapper.insert(msg);
    }

    /**
     * 把附件 DTO 转为存库用的 Map 列表。
     * <p>只保留前端渲染与后续解析所需的字段，避免把冗余信息写进 JSON 列。</p>
     */
    private List<Map<String, Object>> toAttachmentMaps(List<AttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> list = new ArrayList<>(attachments.size());
        for (AttachmentDTO att : attachments) {
            if (att == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fileId", att.getFileId());
            row.put("url", att.getUrl());
            row.put("fileName", att.getFileName());
            row.put("category", att.getCategory());
            row.put("contentType", att.getContentType());
            row.put("fileSize", att.getFileSize());
            list.add(row);
        }
        return list.isEmpty() ? null : list;
    }

    /**
     * 查询全部会话（按开始时间倒序，最新的在前；兜底最多 200 条）
     */
    public List<ChatSession> listSessions() {
        return this.list(new LambdaQueryWrapper<ChatSession>()
                .orderByDesc(ChatSession::getStartTime)
                .last("limit 200"));
    }

    /**
     * 分页查询会话，支持状态、类型与时间范围过滤。
     */
    public PageResult<ChatSession> pageSessions(int pageNum, int pageSize, Integer sessionStatus,
                                                Integer sessionType, LocalDateTime startTime, LocalDateTime endTime) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int page = Math.max(pageNum, 1);
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (sessionStatus != null) {
            wrapper.eq(ChatSession::getSessionStatus, sessionStatus);
        }
        if (sessionType != null) {
            wrapper.eq(ChatSession::getSessionType, sessionType);
        }
        if (startTime != null) {
            wrapper.ge(ChatSession::getStartTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(ChatSession::getStartTime, endTime);
        }
        wrapper.orderByDesc(ChatSession::getStartTime);
        Page<ChatSession> result = this.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
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
        fireWebhook("session_end", session);
    }

    /**
     * 转人工：会话类型改为人工，状态保持进行中（占位）
     *
     * <p>TODO 后续实现：按「在线 + 当前接待数最少」分配坐席，并发时用 Redis 短锁降低重复分配；
     * 无在线坐席时仍改类型，agentId 留空，由班长后续手动派单；并回传坐席工号/姓名供前端接待卡展示。</p>
     *
     * @param sessionId 会话标识
     * @return 接待卡信息，占位时只带默认技能组、无坐席
     */
    public TransferResultDTO markTransferred(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException("会话ID不能为空");
        }
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(sessionId);
        ChatSession session = ensureSession(dto);
        session.setSessionType(2);
        session.setSessionStatus(SessionStatusEnum.ONGOING.getCode());
        this.updateById(session);
        fireWebhook("transfer_agent", session);
        log.info("[占位] 转人工（暂不分配坐席）sessionId={}", sessionId);

        TransferResultDTO result = new TransferResultDTO();
        result.setSkill("综合客服");
        result.setWaitSeconds(0);
        return result;
    }

    /**
     * 把指定访客的匿名会话合并到已登录客户。
     * 只更新 visitor_ref 匹配且 customer_id 为空或 0 的行，避免误伤其他访客。
     *
     * @param visitorRef 外部访客标识
     * @param customerId 登录后的内部客户 ID
     * @return 更新行数
     */
    public int mergeAnonymousSessions(String visitorRef, Long customerId) {
        if (!StringUtils.hasText(visitorRef) || customerId == null || customerId <= 0L) {
            throw new BusinessException("访客标识和客户ID不能为空");
        }
        boolean ok = this.update(new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getVisitorRef, visitorRef.trim())
                .and(w -> w.eq(ChatSession::getCustomerId, 0L).or().isNull(ChatSession::getCustomerId))
                .set(ChatSession::getCustomerId, customerId));
        int rows = ok ? 1 : 0;
        log.info("匿名会话合并 visitorRef={} customerId={} updated={}", visitorRef, customerId, ok);
        return rows;
    }

    /**
     * 异步触发出站 Webhook（占位）
     *
     * <p>TODO 后续实现：组装 sessionId/customerId/agentId/sessionType/sessionStatus 事件负载，
     * 异步推送给订阅方；失败只记日志，不回滚会话状态。</p>
     *
     * @param eventType 事件类型
     * @param session   会话
     */
    @Async
    public void fireWebhook(String eventType, ChatSession session) {
        log.info("[占位] 触发出站 Webhook eventType={} sessionId={}",
                eventType, session == null ? null : session.getSessionId());
    }
}
