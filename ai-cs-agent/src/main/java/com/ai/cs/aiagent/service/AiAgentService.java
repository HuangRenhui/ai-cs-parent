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

    /**
     * AI 对话主流程：参数校验 → 意图识别 → 按意图路由到对应处理器 → 组装回复
     *
     * @param dto 对话请求（消息、会话 ID、租户、实体等）
     * @return 包含回复文本、意图、实体、是否转人工、引用来源的完整结果
     */
    public ChatReplyDTO chatDetail(ChatDTO dto) {
        if (dto == null) {
            throw new BusinessException("消息内容不能为空");
        }
        // 消息长度限制 1~2000，防止超长输入打爆模型上下文
        ValidateUtil.requireLength(dto.getMsg(), "消息内容", 1, 2000);
        dto.setMsg(ValidateUtil.trimToNull(dto.getMsg()));
        // 未传会话 ID 时自动生成，保证后续上下文、幂等键都有稳定标识
        if (!StringUtils.hasText(dto.getSessionId())) {
            dto.setSessionId("sess_" + UUID.randomUUID().toString(true));
        }
        ValidateUtil.optionalSessionId(dto.getSessionId());

        // 第一步：调用大模型做意图识别
        IntentDTO intentDTO = llmUtil.getIntent(dto.getMsg());
        ChatReplyDTO result = new ChatReplyDTO();
        result.setSessionId(dto.getSessionId());
        // 模型降级（调用失败/解析失败）时直接返回繁忙文案，不再走后续业务路由
        if (intentDTO != null && intentDTO.isLlmDegraded()) {
            result.setIntent(IntentEnum.CONSULT.getName());
            result.setEntity("");
            result.setReply(PromptConst.LLM_BUSY_REPLY);
            return result;
        }

        // 第二步：意图与实体归一化；模型没抽出实体时，回退使用请求方携带的实体（如场景入口传入的订单号）
        IntentEnum intent = IntentEnum.fromName(intentDTO.getIntent());
        String entity = intentDTO.getEntity() == null ? "" : intentDTO.getEntity().trim();
        if (!StringUtils.hasText(entity) && dto.getEntities() != null && !dto.getEntities().isEmpty()
                && dto.getEntities().get(0) != null) {
            entity = dto.getEntities().get(0).getId() == null ? "" : dto.getEntities().get(0).getId();
        }

        result.setIntent(intent.getName());
        result.setEntity(entity);
        result.setTransferred(intent == IntentEnum.TO_AGENT);

        // 第三步：意图分支路由——转人工、物流/退款走开放工具、投诉建工单、其余走知识库咨询
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

    /**
     * 处理转人工：通知会话服务把会话状态改为人工接待，回复固定安抚文案
     */
    private String handleTransfer(ChatDTO dto) {
        if (sessionFeign != null && StringUtils.hasText(dto.getSessionId())) {
            try {
                Result<String> transferred = sessionFeign.transfer(dto.getSessionId());
                if (transferred == null || !transferred.isOk()) {
                    // 状态变更失败只记日志，不影响给用户的回复
                    log.warn("转人工改会话状态失败: {}", transferred == null ? "empty" : transferred.getMsg());
                }
            } catch (Exception e) {
                log.warn("转人工改会话状态异常: {}", e.getMessage());
            }
        }
        return PromptConst.TRANSFER_REPLY;
    }

    /**
     * 处理咨询类意图：走知识库 RAG 检索，命中返回知识回复，未命中/不可用返回对应兜底文案
     */
    private String handleConsult(ChatDTO dto, ChatReplyDTO result) {
        if (knowledgeFeign == null) {
            result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
            return PromptConst.LLM_BUSY_REPLY;
        }
        // 租户缺省按 default 处理，保证多租户检索隔离
        String tenant = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode().trim() : "default";
        try {
            // 场景入口带来的实体（如产品/订单号）并入检索 query，提升 BM25 对关联文档的命中
            String query = dto.getMsg();
            if (dto.getEntities() != null && !dto.getEntities().isEmpty() && dto.getEntities().get(0) != null
                    && StringUtils.hasText(dto.getEntities().get(0).getId())
                    && !query.contains(dto.getEntities().get(0).getId())) {
                query = query + " " + dto.getEntities().get(0).getId();
            }
            Result<RagSearchResultDTO> rag = knowledgeFeign.ragSearch(query, tenant, dto.getSessionId());
            if (rag == null || !rag.isOk() || rag.getData() == null) {
                // 检索服务异常：标记不可用并返回繁忙文案
                result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
                log.warn("知识库检索不可用: {}", rag == null ? "empty" : rag.getMsg());
                return PromptConst.LLM_BUSY_REPLY;
            }
            RagSearchResultDTO data = rag.getData();
            result.setKnowledgeStatus(data.getStatus());
            result.setCitations(data.getCitations());
            if (RagSearchResultDTO.HIT.equals(data.getStatus()) && StringUtils.hasText(data.getReply())) {
                // 命中知识：直接使用知识库生成的回复（含引用）
                return data.getReply();
            }
            if (RagSearchResultDTO.MISS.equals(data.getStatus())) {
                // 明确未命中：提示用户换个问法或转人工
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

    /**
     * 通过开放工具处理物流查询/退款意图：构造工具调用请求并走幂等控制
     *
     * @param intent 意图（QUERY_LOGISTICS / REFUND）
     * @param entity 业务实体（通常为订单号）
     * @param dto    原始对话请求
     * @return 工具执行结果文案；缺少实体或服务不可用时返回引导话术
     */
    private String handleByTool(IntentEnum intent, String entity, ChatDTO dto) {
        // 没有订单号无法执行工具，先向用户追问
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
            // 退款等敏感操作需要用户消息里出现"确认"才真正执行，防止误触
            invoke.setConfirmed(dto.getMsg() != null && dto.getMsg().contains("确认"));
            // 幂等键 = 会话+意图+实体，重复提交/网络重试不会重复执行
            if (StringUtils.hasText(dto.getSessionId())) {
                invoke.setIdempotencyKey(dto.getSessionId() + ":" + intent.getName() + ":" + entity);
            }
            // 请求方携带了实体信息时补充实体类型与实体 ID
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
                // 业务性失败（未绑定工具/未注册/待确认/幂等命中）也直接透传工具的提示文案
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

    /**
     * 开放工具不可用时的兜底话术：按意图给出具体引导，避免生硬报错
     */
    private String toolUnavailable(IntentEnum intent, String entity) {
        if (intent == IntentEnum.QUERY_LOGISTICS) {
            return "开放能力暂时不可用，无法查询订单【" + entity + "】的物流。请稍后重试，或选择转人工客服。";
        }
        if (intent == IntentEnum.REFUND) {
            return "开放能力暂时不可用，无法为订单【" + entity + "】提交退款。请稍后重试，或选择转人工客服。";
        }
        return PromptConst.TOOL_BUSY_REPLY;
    }

    /**
     * 处理投诉意图：自动创建投诉工单并告知用户单号；建单失败时给出人工引导
     */
    private String handleComplaint(ChatDTO dto, String entity) {
        try {
            WorkOrderDTO orderDTO = new WorkOrderDTO();
            orderDTO.setOrderType("投诉");
            // 实体（订单号等）附加到投诉内容中，便于客服定位问题
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
