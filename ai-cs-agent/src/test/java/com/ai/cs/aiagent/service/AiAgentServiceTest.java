package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.util.LlmUtil;
import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AiAgentService 单元测试：覆盖意图路由主链路——转人工、查物流降级、知识库命中/不可用、意图模型降级。
 * 全部下游（LLM、知识库、开放工具、会话、工单）以 Mock 隔离，只验证编排逻辑与兜底话术。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiAgentService 单元测试")
class AiAgentServiceTest {

    /** 大模型调用入口（意图识别/对话生成），Mock 掉以固定意图返回 */
    @Mock
    private LlmUtil llmUtil;

    @Mock
    private WorkOrderFeign workOrderFeign;

    /** 知识库检索（RAG），用于验证命中/不可用两种分支 */
    @Mock
    private KnowledgeFeign knowledgeFeign;

    /** 开放工具调用（查物流/退款等），用于验证工具失败时的降级话术 */
    @Mock
    private OpenToolFeign openToolFeign;

    /** 会话服务（转人工），用于验证转接动作确实触发 */
    @Mock
    private SessionFeign sessionFeign;

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
        when(sessionFeign.transfer(anyString())).thenReturn(Result.success("ok"));

        String reply = aiAgentService.chat(chatDTO);
        assertTrue(reply.contains("人工客服"));
        verify(sessionFeign).transfer("session-001");
    }

    @Test
    @DisplayName("查物流失败不声称已发货")
    void testChat_QueryLogistics_ToolDown() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("查物流");
        intent.setEntity("ORDER-123");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);
        when(openToolFeign.invoke(any())).thenReturn(Result.fail(503, "down"));

        String reply = aiAgentService.chat(chatDTO);
        assertTrue(reply.contains("ORDER-123"));
        assertTrue(reply.contains("暂时不可用"));
        assertFalse(reply.contains("已发货"));
        assertFalse(reply.contains("运输中"));
    }

    @Test
    @DisplayName("查物流无订单号")
    void testChat_QueryLogistics_NoEntity() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("查物流");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        String reply = aiAgentService.chat(chatDTO);
        assertTrue(reply.contains("请提供"));
        verify(openToolFeign, never()).invoke(any());
    }

    @Test
    @DisplayName("知识库不可用返回繁忙话术而不是闲聊")
    void testChat_KnowledgeUnavailable() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("咨询");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);
        when(knowledgeFeign.ragSearch(any(), any(), any())).thenReturn(Result.fail(503, "down"));

        String reply = aiAgentService.chat(chatDTO);
        assertEquals(PromptConst.LLM_BUSY_REPLY, reply);
        verify(llmUtil, never()).chatReply(anyString(), anyString());
    }

    @Test
    @DisplayName("意图模型失败返回繁忙话术")
    void testChat_IntentDegraded() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("咨询");
        intent.setLlmDegraded(true);
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        String reply = aiAgentService.chat(chatDTO);
        assertEquals(PromptConst.LLM_BUSY_REPLY, reply);
        verifyNoInteractions(knowledgeFeign);
    }

    @Test
    @DisplayName("知识库命中返回资料答复")
    void testChat_KnowledgeHit() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("咨询");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);
        RagSearchResultDTO hit = RagSearchResultDTO.hit("根据资料：营业时间 9 点", java.util.List.of());
        when(knowledgeFeign.ragSearch(any(), any(), any())).thenReturn(Result.success(hit));

        String reply = aiAgentService.chat(chatDTO);
        assertEquals("根据资料：营业时间 9 点", reply);
    }
}
