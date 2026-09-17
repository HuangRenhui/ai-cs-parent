package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 流式输出与打断服务（占位）
 *
 * <p>TODO 后续实现：在线程池内调用 {@code ModelRouter.chatForType(LLM, messages, sessionId)} 取全量回复，
 * 按固定片段长度逐段通过 SSE 的 {@code token} 事件推送，每段之间让出时间；用户打断时推送
 * {@code interrupted} 事件并结束；结束推送 {@code done}；模型异常推送 {@code error}。
 * 打断标记需用 {@link java.util.concurrent.atomic.AtomicBoolean} 保证并发可见性。</p>
 *
 * <p>当前不调用模型：{@link #streamChat} 直接回一个 {@code error} 事件并结束，
 * {@link #interrupt} 恒返回 false（无流可打断），{@link #isStreaming} 恒返回 false。</p>
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class StreamingChatService {

    @Resource
    private ModelRouter modelRouter;

    /**
     * 流式对话（占位：不调用模型，直接结束 SSE 并回错误事件）
     *
     * @param sessionId 会话ID
     * @param messages  消息列表
     * @param emitter   SSE发射器
     */
    public void streamChat(String sessionId, List<Map<String, String>> messages, SseEmitter emitter) {
        log.warn("[占位] 流式对话未实现 sessionId={}，直接结束 SSE", sessionId);
        try {
            emitter.send(SseEmitter.event().name("error").data("流式输出为占位实现，后端未接入模型流式调用"));
            emitter.complete();
        } catch (IOException e) {
            // 客户端可能已断开，仅记日志
            log.debug("SSE 发送占位提示失败（客户端可能已断开）sessionId={}", sessionId);
        }
    }

    /**
     * 用户打断当前流式输出（占位：无流可打断，恒返回 false）
     *
     * @param sessionId 会话ID
     * @return false
     */
    public boolean interrupt(String sessionId) {
        log.info("[占位] 流式打断未实现 sessionId={}", sessionId);
        return false;
    }

    /**
     * 检查会话是否正在流式输出中（占位：恒返回 false）
     *
     * @param sessionId 会话ID
     * @return false
     */
    public boolean isStreaming(String sessionId) {
        return false;
    }
}
