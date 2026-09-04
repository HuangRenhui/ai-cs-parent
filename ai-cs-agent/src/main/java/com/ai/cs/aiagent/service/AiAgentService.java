package com.ai.cs.aiagent.service;

import cn.hutool.core.lang.UUID;
import com.ai.cs.aiagent.util.LlmUtil;
import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.ChatReplyDTO;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.enums.IntentEnum;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.util.ValidateUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI智能体服务
 * 负责意图识别、业务路由、开放工具调用和知识库咨询
 *
 * @author huangrenhui
 * @date 2026/6/11 17:55
 */
@Slf4j
@Service
public class AiAgentService {

    @Resource
    private LlmUtil llmUtil;

    @Resource
    private WorkOrderFeign workOrderFeign;

    @Resource
    private KnowledgeFeign knowledgeFeign;

    @Resource
    private OpenToolFeign openToolFeign;

    @Resource
    private SessionFeign sessionFeign;

    /**
     * 机器人自动回复（兼容原有字符串接口）
     */
    public String chat(ChatDTO dto) {
        return chatDetail(dto).getReply();
    }

    public ChatReplyDTO chatDetail(ChatDTO dto) {
        if (dto == null) {
            throw new BusinessException("消息内容不能为空");
        }
        ValidateUtil.requireLength(dto.getMsg(), "消息内容", 1, 2000);
        dto.setMsg(ValidateUtil.trimToNull(dto.getMsg()));
        if (!StringUtils.hasText(dto.getSessionId())) {
            dto.setSessionId("sess_" + UUID.randomUUID().toString(true));
        }
        ValidateUtil.optionalSessionId(dto.getSessionId());

        IntentDTO intentDTO = llmUtil.getIntent(dto.getMsg());
        ChatReplyDTO result = new ChatReplyDTO();
        result.setSessionId(dto.getSessionId());
        if (intentDTO != null && intentDTO.isLlmDegraded()) {
            result.setIntent(IntentEnum.CONSULT.getName());
            result.setEntity("");
            result.setReply(PromptConst.LLM_BUSY_REPLY);
            return result;
        }

        IntentEnum intent = IntentEnum.fromName(intentDTO.getIntent());
        String entity = intentDTO.getEntity() == null ? "" : intentDTO.getEntity().trim();
        if (!StringUtils.hasText(entity) && dto.getEntities() != null && !dto.getEntities().isEmpty()
                && dto.getEntities().get(0) != null) {
            entity = dto.getEntities().get(0).getId() == null ? "" : dto.getEntities().get(0).getId();
        }

        result.setIntent(intent.getName());
        result.setEntity(entity);
        result.setTransferred(intent == IntentEnum.TO_AGENT);

        String reply = switch (intent) {
            case TO_AGENT -> handleTransfer(dto);
            case QUERY_LOGISTICS -> handleByTool(intent, entity, dto);
            case REFUND -> handleByTool(intent, entity, dto);
            case COMPLAINT -> handleComplaint(dto, entity);
            default -> handleConsult(dto, result);
        };
        result.setReply(reply);
        return result;
    }

    private String handleTransfer(ChatDTO dto) {
        if (sessionFeign != null && StringUtils.hasText(dto.getSessionId())) {
            try {
                Result<String> transferred = sessionFeign.transfer(dto.getSessionId());
                if (transferred == null || !transferred.isOk()) {
                    log.warn("转人工改会话状态失败: {}", transferred == null ? "empty" : transferred.getMsg());
                }
            } catch (Exception e) {
                log.warn("转人工改会话状态异常: {}", e.getMessage());
            }
        }
        return PromptConst.TRANSFER_REPLY;
    }

    private String handleConsult(ChatDTO dto, ChatReplyDTO result) {
        if (knowledgeFeign == null) {
            result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
            return PromptConst.LLM_BUSY_REPLY;
        }
        String tenant = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode().trim() : "default";
        try {
            Result<RagSearchResultDTO> rag = knowledgeFeign.ragSearch(dto.getMsg(), tenant, dto.getSessionId());
            if (rag == null || !rag.isOk() || rag.getData() == null) {
                result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
                log.warn("知识库检索不可用: {}", rag == null ? "empty" : rag.getMsg());
                return PromptConst.LLM_BUSY_REPLY;
            }
            RagSearchResultDTO data = rag.getData();
            result.setKnowledgeStatus(data.getStatus());
            result.setCitations(data.getCitations());
            if (RagSearchResultDTO.HIT.equals(data.getStatus()) && StringUtils.hasText(data.getReply())) {
                return data.getReply();
            }
            if (RagSearchResultDTO.MISS.equals(data.getStatus())) {
                return PromptConst.NO_KNOWLEDGE_REPLY;
            }
            result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
            return PromptConst.LLM_BUSY_REPLY;
        } catch (Exception e) {
            result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
            log.warn("知识库检索失败: {}", e.getMessage());
            return PromptConst.LLM_BUSY_REPLY;
        }
    }

    private String handleByTool(IntentEnum intent, String entity, ChatDTO dto) {
        if (!StringUtils.hasText(entity)) {
            return intent == IntentEnum.REFUND
                    ? "请告诉我需要退款的订单编号"
                    : "请提供您的订单号，我马上为您查询物流进度";
        }
        if (openToolFeign == null) {
            return toolUnavailable(intent, entity);
        }
        try {
            ToolInvokeDTO invoke = new ToolInvokeDTO();
            invoke.setIntentBind(intent.getName());
            invoke.setEntityId(entity);
            invoke.setSessionId(dto.getSessionId());
            invoke.setConfirmed(dto.getMsg() != null && dto.getMsg().contains("确认"));
            if (StringUtils.hasText(dto.getSessionId())) {
                invoke.setIdempotencyKey(dto.getSessionId() + ":" + intent.getName() + ":" + entity);
            }
            if (dto.getEntities() != null && !dto.getEntities().isEmpty() && dto.getEntities().get(0) != null) {
                invoke.setEntityType(dto.getEntities().get(0).getType());
                if (!StringUtils.hasText(invoke.getEntityId())) {
                    invoke.setEntityId(dto.getEntities().get(0).getId());
                }
            }
            Result<ToolInvokeResultDTO> res = openToolFeign.invoke(invoke);
            if (res != null && res.isOk() && res.getData() != null) {
                ToolInvokeResultDTO data = res.getData();
                if (data.isSuccess() && StringUtils.hasText(data.getOutput())) {
                    return data.getOutput();
                }
                if (!data.isSuccess() && StringUtils.hasText(data.getOutput())
                        && ("pack-off".equals(data.getSource()) || "registry".equals(data.getSource())
                        || "confirm-required".equals(data.getSource()) || "idempotent".equals(data.getSource()))) {
                    return data.getOutput();
                }
                if (!data.isSuccess() && StringUtils.hasText(data.getOutput())) {
                    return data.getOutput();
                }
            }
        } catch (Exception e) {
            log.warn("开放工具调用失败: {}", e.getMessage());
        }
        return toolUnavailable(intent, entity);
    }

    private String toolUnavailable(IntentEnum intent, String entity) {
        if (intent == IntentEnum.QUERY_LOGISTICS) {
            return "开放能力暂时不可用，无法查询订单【" + entity + "】的物流。请稍后重试，或选择转人工客服。";
        }
        if (intent == IntentEnum.REFUND) {
            return "开放能力暂时不可用，无法为订单【" + entity + "】提交退款。请稍后重试，或选择转人工客服。";
        }
        return PromptConst.TOOL_BUSY_REPLY;
    }

    private String handleComplaint(ChatDTO dto, String entity) {
        try {
            WorkOrderDTO orderDTO = new WorkOrderDTO();
            orderDTO.setOrderType("投诉");
            String content = dto.getMsg();
            if (StringUtils.hasText(entity)) {
                content = content + "（关联信息：" + entity + "）";
            }
            orderDTO.setContent(content);
            orderDTO.setSessionId(dto.getSessionId());
            orderDTO.setCustomerId(dto.getCustomerId() == null ? 0L : dto.getCustomerId());
            Result<String> created = workOrderFeign.createOrder(orderDTO);
            if (created != null && created.isOk()) {
                return "非常抱歉给您带来不佳体验。您的投诉工单已提交：" + created.getData() + "，我们会尽快处理。";
            }
        } catch (Exception e) {
            log.error("投诉工单创建失败", e);
        }
        return "非常抱歉给您带来不佳体验。工单服务暂时繁忙，请稍后在「工单管理」中手动提交，或转接人工客服。";
    }
}
