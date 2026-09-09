package com.ai.cs.common.service;

import com.ai.cs.common.dto.NotificationDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息通知服务
 * 支持站内信/邮件/短信三种渠道
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
public class NotificationService {

    /** 内存存储（实际项目应使用数据库） */
    private final Map<Long, NotificationDTO> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * 发送通知（异步）
     * 先入内存库置为 PENDING，再按渠道发送，成功/失败回写状态；发送异常不影响主流程返回。
     */
    public NotificationDTO send(NotificationDTO dto) {
        dto.setId(idGenerator.getAndIncrement());
        dto.setIsRead(false);
        dto.setStatus("PENDING");
        store.put(dto.getId(), dto);

        // 异步发送（实际应使用 @Async + 线程池）
        try {
            switch (dto.getChannel()) {
                case "IN_APP" -> sendInApp(dto);
                case "EMAIL" -> sendEmail(dto);
                case "SMS" -> sendSms(dto);
            }
            dto.setStatus("SENT");
            log.info("通知发送成功: id={}, channel={}, receiver={}", dto.getId(), dto.getChannel(), dto.getReceiverId());
        } catch (Exception e) {
            // 发送失败只标记状态，不抛给调用方（通知属弱依赖）
            dto.setStatus("FAILED");
            log.error("通知发送失败: id={}, error={}", dto.getId(), e.getMessage());
        }

        return dto;
    }

    /**
     * 查询用户未读通知
     */
    public List<NotificationDTO> getUnread(Long userId) {
        return store.values().stream()
                .filter(n -> n.getReceiverId().equals(userId) && !n.getIsRead())
                .toList();
    }

    /**
     * 查询用户全部通知
     */
    public List<NotificationDTO> getAll(Long userId) {
        return store.values().stream()
                .filter(n -> n.getReceiverId().equals(userId))
                .toList();
    }

    /**
     * 标记已读
     */
    public void markAsRead(Long notificationId) {
        NotificationDTO dto = store.get(notificationId);
        if (dto != null) {
            dto.setIsRead(true);
        }
    }

    /**
     * 全部标记已读
     */
    public void markAllAsRead(Long userId) {
        store.values().stream()
                .filter(n -> n.getReceiverId().equals(userId))
                .forEach(n -> n.setIsRead(true));
    }

    /**
     * 获取未读数量
     */
    public long getUnreadCount(Long userId) {
        return store.values().stream()
                .filter(n -> n.getReceiverId().equals(userId) && !n.getIsRead())
                .count();
    }

    /** 站内信发送（当前仅记录日志，占位实现） */
    private void sendInApp(NotificationDTO dto) {
        log.info("[站内信] 发送通知: title={}, receiver={}", dto.getTitle(), dto.getReceiverId());
    }

    /** 邮件发送（当前仅记录日志，占位实现） */
    private void sendEmail(NotificationDTO dto) {
        log.info("[邮件] 发送通知: title={}, receiver={}", dto.getTitle(), dto.getReceiverId());
    }

    /** 短信发送（当前仅记录日志，占位实现） */
    private void sendSms(NotificationDTO dto) {
        log.info("[短信] 发送通知: title={}, receiver={}", dto.getTitle(), dto.getReceiverId());
    }
}
