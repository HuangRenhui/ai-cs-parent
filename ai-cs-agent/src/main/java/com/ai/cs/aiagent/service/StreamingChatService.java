package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 流式输出与打断服务
 * 补全功能清单 P1 缺失「流式输出与打断」
 * 支持SSE逐token推送，用户可中途打断
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class StreamingChatService {

    @Resource
    private ModelRouter modelRouter;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "streaming-chat");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, volatile Boolean> interruptFlags = new ConcurrentHashMap<>();

    /**
     * 流式对话：逐token通过SSE推送给前端，支持中途打断
     *
     * @param sessionId 会话ID
     * @param messages  消息列表
     * @param emitter   SSE发射器
     */
    public void streamChat(String sessionId, List<Map<String, String>> messages, SseEmitter emitter) {
        interruptFlags.put(sessionId, false);

        executor.submit(() -> {
            try {
                String fullResponse = modelRouter.chatForType(ModelTypeEnum.LLM.getCode(), messages, sessionId);
                if (fullResponse == null || fullResponse.isBlank()) {
                    emitter.send(SseEmitter.event().name("error").data("模型返回为空"));
                    emitter.complete();
                    return;
                }

                int chunkSize = 2;
                StringBuilder sent = new StringBuilder();
                for (int i = 0; i < fullResponse.length(); i += chunkSize) {
                    if (Boolean.TRUE.equals(interruptFlags.get(sessionId))) {
                        emitter.send(SseEmitter.event().name("interrupted").data(sent.toString()));
                        emitter.complete();
                        log.info("流式输出被用户打断 sessionId={}", sessionId);
                        return;
                    }
                    int end = Math.min(i + chunkSize, fullResponse.length());
                    String chunk = fullResponse.substring(i, end);
                    emitter.send(SseEmitter.event().name("token").data(chunk));
                    sent.append(chunk);
                    Thread.sleep(30);
                }
                emitter.send(SseEmitter.event().name("done").data(sent.toString()));
                emitter.complete();
            } catch (ModelCallException e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("模型调用失败: " + e.getMessage()));
                    emitter.complete();
                } catch (IOException ignored) {
                }
                log.error("流式对话模型调用失败 sessionId={}", sessionId, e);
            } catch (IOException e) {
                log.debug("SSE发送异常（客户端可能已断开）sessionId={}", sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("流式对话异常 sessionId={}", sessionId, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                }
            } finally {
                interruptFlags.remove(sessionId);
            }
        });
    }

    /**
     * 用户打断当前流式输出
     *
     * @param sessionId 会话ID
     * @return true=打断成功
     */
    public boolean interrupt(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        interruptFlags.put(sessionId, true);
        log.info("用户请求打断流式输出 sessionId={}", sessionId);
        return true;
    }

    /**
     * 检查会话是否正在流式输出中
     */
    public boolean isStreaming(String sessionId) {
        return sessionId != null && interruptFlags.containsKey(sessionId);
    }
}