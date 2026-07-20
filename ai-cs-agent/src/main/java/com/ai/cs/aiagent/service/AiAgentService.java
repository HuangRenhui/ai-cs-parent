package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.util.LlmUtil;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.enums.IntentEnum;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

/**
 * AI智能体服务
 * 负责意图识别、业务路由和自动回复
 *
 * @author huangrenhui
 * @date 2026/6/11 17:55
 */
@Service
public class AiAgentService {

    @Resource
    private LlmUtil llmUtil;

    @Resource
    private WorkOrderFeign workOrderFeign;

    /**
     * 机器人自动回复
     *
     * @param dto 聊天DTO
     * @return 回复内容
     */
    public String chat(ChatDTO dto) {
        String userMsg = dto.getMsg();
        String history = dto.getHistory();
        String sessionId = dto.getSessionId();

        // 1. 意图识别
        IntentDTO intentDTO = llmUtil.getIntent(userMsg);
        String intent = intentDTO.getIntent();
        String entity = intentDTO.getEntity();

        // 2. 业务路由（使用 IntentEnum 进行类型安全匹配）
        if (IntentEnum.TO_AGENT.getName().equals(intent)) {
            return "已为您转接人工客服，请耐心等待~";
        } else if (IntentEnum.QUERY_LOGISTICS.getName().equals(intent)) {
            return handleQueryLogistics(entity);
        } else if (IntentEnum.REFUND.getName().equals(intent)) {
            return handleRefund(entity);
        } else if (IntentEnum.COMPLAINT.getName().equals(intent)) {
            return handleComplaint(sessionId, userMsg);
        } else if ("咨询".equals(intent) || "查知识库".equals(intent)) {
            return handleKnowledgeQuery(userMsg, history);
        }
        return llmUtil.chatReply(userMsg, history);
    }

    // 查物流
    private String handleQueryLogistics(String entity) {
        if (entity == null || entity.isBlank()) {
            return "请提供您的订单号，我马上为您查询物流进度";
        }
        return "订单【" + entity + "】当前物流：已发货，运输中";
    }

    // 退款
    private String handleRefund(String entity) {
        if (entity == null || entity.isBlank()) {
            return "请告诉我需要退款的订单编号";
        }
        return "已为订单【" + entity + "】提交退款申请，请等待审核";
    }

    // 投诉 -> 自动创建工单
    private String handleComplaint(String sessionId, String content) {
        WorkOrderDTO orderDTO = new WorkOrderDTO();
        orderDTO.setOrderType("投诉");
        orderDTO.setContent(content);
        orderDTO.setSessionId(sessionId);
        orderDTO.setCustomerId(0L);
        workOrderFeign.createOrder(orderDTO);
        return "非常抱歉给您带来不佳体验！您的投诉工单已成功提交，我们会尽快处理。";
    }

    // 知识库查询
    private String handleKnowledgeQuery(String userMsg, String history) {
        return llmUtil.chatReply(userMsg, history);
    }
}