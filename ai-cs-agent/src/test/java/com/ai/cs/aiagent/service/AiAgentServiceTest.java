package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.util.LlmUtil;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.IntentDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AiAgentService 单元测试
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiAgentService 单元测试")
class AiAgentServiceTest {

    @Mock
    private LlmUtil llmUtil;

    @Mock
    private WorkOrderFeign workOrderFeign;

    @InjectMocks
    private AiAgentService aiAgentService;

    private ChatDTO chatDTO;

    @BeforeEach
    void setUp() {
        chatDTO = new ChatDTO();
        chatDTO.setSessionId("session-001");
        chatDTO.setMsg("你好");
        chatDTO.setHistory("");
    }

    @Test
    @DisplayName("意图识别 - 转人工")
    void testChat_TransferToAgent() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("转人工");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        String reply = aiAgentService.chat(chatDTO);
        assertTrue(reply.contains("人工客服"));
    }

    @Test
    @DisplayName("意图识别 - 查物流（有订单号）")
    void testChat_QueryLogistics_WithEntity() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("查物流");
        intent.setEntity("ORDER-123");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        String reply = aiAgentService.chat(chatDTO);
        assertTrue(reply.contains("ORDER-123"));
        assertTrue(reply.contains("运输中"));
    }

    @Test
    @DisplayName("意图识别 - 查物流（无订单号）")
    void testChat_QueryLogistics_NoEntity() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("查物流");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        String reply = aiAgentService.chat(chatDTO);
        assertTrue(reply.contains("请提供"));
    }

    @Test
    @DisplayName("意图识别 - 默认LLM回复")
    void testChat_DefaultLLMReply() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("其他");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);
        when(llmUtil.chatReply(anyString(), anyString())).thenReturn("这是AI的默认回复");

        String reply = aiAgentService.chat(chatDTO);
        assertEquals("这是AI的默认回复", reply);
    }
}
