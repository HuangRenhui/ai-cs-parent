package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.util.LlmUtil;
import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.constant.PromptConst;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.withSettings;

/**
 * AiAgentService 单元测试：业务路由未实现，只验证「入参校验」与「占位语义」。
 *
 * <p>校验与安全检查为真实现，需保留断言；意图路由、开放工具、会话转接、知识库检索
 * 均已为占位实现，因此断言它们<strong>不被调用</strong>且统一返回繁忙文案。</p>
 *
 * <p>待业务路由补齐后，本用例应改回「转人工回填坐席 / 查物流降级话术 / 知识库命中」的真实断言。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiAgentService 单元测试（业务路由为占位）")
class AiAgentServiceTest {

    /** 大模型调用入口（意图识别/对话生成），Mock 掉以固定意图返回 */
    @Mock
    private LlmUtil llmUtil;

    @Mock
    private WorkOrderFeign workOrderFeign;

    /** 知识库检索（RAG），占位态下不应被调用 */
    @Mock
    private KnowledgeFeign knowledgeFeign;

    /** 开放工具调用（查物流/退款等），占位态下不应被调用 */
    @Mock
    private OpenToolFeign openToolFeign;

    /** 会话服务（转人工），占位态下不应被调用 */
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
    @DisplayName("入参校验 - 消息为空抛异常")
    void testChat_EmptyMsg() {
        chatDTO.setMsg(null);
        assertThrows(RuntimeException.class, () -> aiAgentService.chat(chatDTO));
    }

    @Test
    @DisplayName("入参校验 - 超长消息抛异常")
    void testChat_TooLongMsg() {
        chatDTO.setMsg("a".repeat(2001));
        assertThrows(RuntimeException.class, () -> aiAgentService.chat(chatDTO));
    }

    @Test
    @DisplayName("意图模型降级返回繁忙话术")
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
    @DisplayName("占位：转人工意图不再调用会话服务")
    void testChat_TransferPlaceholder() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("转人工");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        assertEquals(PromptConst.LLM_BUSY_REPLY, aiAgentService.chat(chatDTO));
        verify(sessionFeign, never()).transfer(anyString());
    }

    @Test
    @DisplayName("占位：查物流意图不再调用开放工具")
    void testChat_LogisticsPlaceholder() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("查物流");
        intent.setEntity("ORDER-123");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        assertEquals(PromptConst.LLM_BUSY_REPLY, aiAgentService.chat(chatDTO));
        verify(openToolFeign, never()).invoke(any());
    }

    @Test
    @DisplayName("占位：咨询意图不再检索知识库、不建工单")
    void testChat_ConsultPlaceholder() {
        IntentDTO intent = new IntentDTO();
        intent.setIntent("咨询");
        when(llmUtil.getIntent(anyString())).thenReturn(intent);

        assertEquals(PromptConst.LLM_BUSY_REPLY, aiAgentService.chat(chatDTO));
        verifyNoInteractions(knowledgeFeign, workOrderFeign);
    }
}
