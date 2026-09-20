package com.ai.cs.aiagent.service;

import cn.hutool.core.lang.UUID;
import com.ai.cs.aiagent.util.LlmUtil;
import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.AttachmentDTO;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.ChatReplyDTO;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.dto.RagSearchResultDTO;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.dto.SlotFillResultDTO;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.dto.TransferResultDTO;
import com.ai.cs.common.dto.WorkOrderDTO;
import com.ai.cs.common.enums.IntentEnum;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.llm.TenantQuotaService;
import com.ai.cs.common.result.Result;
import com.ai.cs.common.util.ContentSafetyChecker;
import com.ai.cs.common.util.PromptInjectionProtection;
import com.ai.cs.common.util.ValidateUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AI智能体服务（业务路由为占位实现）
 *
 * <p><b>保留为真实现</b>：入参校验（非空、长度 1~2000）、租户日配额拦截、
 * 提示词注入防护、内容安全检查与输入清理、会话 ID 兜底生成。
 * 安全类校验即使在下游能力未实现时也必须生效，不做简化。</p>
 *
 * <p>TODO 后续实现（业务路由）：按意图分发——
 * <ul>
 *   <li><b>转人工</b>：{@code SessionFeign.transfer} 改会话状态并回填坐席工号/姓名，
 *       改状态失败时不得返回「已转接」话术；</li>
 *   <li><b>查物流 / 退款</b>：构造 {@code ToolInvokeDTO} 走 {@code OpenToolFeign.invoke}，
 *       退款需用户消息含「确认」才置 confirmed，幂等键为 {@code sessionId:意图:实体}；</li>
 *   <li><b>投诉</b>：经 {@code WorkOrderFeign.createOrder} 建单并回报单号；</li>
 *   <li><b>其余</b>：走 {@code KnowledgeFeign.ragSearch}，按 HIT/MISS/UNAVAILABLE 分别回
 *       知识回复、换问法提示、繁忙文案，并叠加 {@link #resolveIndustryOverlay}。</li>
 * </ul>
 * 当前 {@link #routeByIntent} 统一返回繁忙文案，不调用任何模型与外部服务。</p>
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
    private ConfigurableIntentService configurableIntentService;

    @Resource
    private SlotFillingService slotFillingService;

    @Resource
    private WorkOrderFeign workOrderFeign;

    @Resource
    private KnowledgeFeign knowledgeFeign;

    @Resource
    private OpenToolFeign openToolFeign;

    @Resource
    private SessionFeign sessionFeign;

    @Resource
    private TenantQuotaService tenantQuotaService;

    @Autowired(required = false)
    private IndustryPromptPackService industryPromptPackService;

    /**
     * 敏感操作列表，用于检测绕过尝试
     */
    private static final List<String> SENSITIVE_ACTIONS = Arrays.asList(
        "退款", "冻卡", "销户", "删除", "修改", "转账", "支付"
    );

    /**
     * 机器人自动回复（兼容原有字符串接口）
     */
    public String chat(ChatDTO dto) {
        return chatDetail(dto).getReply();
    }

    /**
     * AI 对话主流程：参数校验 → 安全检查 → 意图识别 → 填槽检查 → 按意图路由 → 组装回复
     *
     * <p>校验与安全检查为真实现；意图识别、填槽与路由的下游能力已为占位实现，
     * 因此当前实际结果是「降级为咨询 + 繁忙文案」。</p>
     *
     * @param dto 对话请求（消息、会话 ID、租户、实体等）
     * @return 包含回复文本、意图、实体、是否转人工、引用来源的完整结果
     */
    public ChatReplyDTO chatDetail(ChatDTO dto) {
        if (dto == null) {
            throw new BusinessException("消息内容不能为空");
        }

        // 附件与文本至少有一项：支持「文字 + 图片/附件」以及「只发附件不说话」两种发法
        List<AttachmentDTO> attachments = dto.getAttachments() == null
                ? new ArrayList<>()
                : dto.getAttachments();
        boolean hasAttachment = !attachments.isEmpty();
        boolean hasText = StringUtils.hasText(dto.getMsg());
        if (!hasText && !hasAttachment) {
            throw new BusinessException("消息内容不能为空");
        }

        if (hasText) {
            // 消息长度限制 1~2000，防止超长输入打爆模型上下文
            ValidateUtil.requireLength(dto.getMsg(), "消息内容", 1, 2000);
        } else {
            // 纯附件消息：msg 置空串而非 null，避免下游拼接上下文时出现 "null"
            dto.setMsg("");
        }
        dto.setMsg(ValidateUtil.trimToNull(dto.getMsg()));
        if (dto.getMsg() == null) {
            dto.setMsg("");
        }
        dto.setAttachments(attachments);
        log.info("[对话] 收到消息 sessionId={} 文本长度={} 附件数={}",
                dto.getSessionId(), dto.getMsg().length(), attachments.size());

        // 租户日配额：超限直接返回繁忙话术，避免把账单打爆
        String tenantCode = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode() : "default";
        dto.setTenantCode(tenantCode);
        if (tenantQuotaService != null && tenantQuotaService.isQuotaExceeded(tenantCode)) {
            ChatReplyDTO quota = new ChatReplyDTO();
            quota.setSessionId(dto.getSessionId());
            quota.setIntent("咨询");
            quota.setEntity("");
            quota.setReply(PromptConst.LLM_BUSY_REPLY);
            return quota;
        }

        // 安全检查：提示词注入防护
        PromptInjectionProtection.SecurityCheckResult securityCheck =
            PromptInjectionProtection.performSecurityCheck(dto.getMsg(), 2000, SENSITIVE_ACTIONS);
        if (!securityCheck.isSafe()) {
            ChatReplyDTO result = new ChatReplyDTO();
            result.setSessionId(dto.getSessionId());
            result.setIntent("咨询");
            result.setEntity("");
            result.setReply("您的输入包含不安全内容，请重新表述。");
            return result;
        }

        // 内容安全检查
        ContentSafetyChecker.SafetyCheckResult safetyCheck = ContentSafetyChecker.check(dto.getMsg());
        if (!safetyCheck.isSafe()) {
            ChatReplyDTO result = new ChatReplyDTO();
            result.setSessionId(dto.getSessionId());
            result.setIntent("咨询");
            result.setEntity("");
            result.setReply(safetyCheck.getReason() + "，请遵守社区规范。");
            return result;
        }

        // 清理输入
        dto.setMsg(PromptInjectionProtection.sanitizeInput(dto.getMsg()));

        // 未传会话 ID 时自动生成，保证后续上下文、幂等键都有稳定标识
        if (!StringUtils.hasText(dto.getSessionId())) {
            dto.setSessionId("sess_" + UUID.randomUUID().toString(true));
        }
        ValidateUtil.optionalSessionId(dto.getSessionId());

        // 第一步：意图识别（委派；两个下游服务均已为占位实现，恒返回降级意图）
        IntentDTO intentDTO = recognizeIntent(dto);
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
        String intentName = intentDTO.getIntent();
        String entity = intentDTO.getEntity() == null ? "" : intentDTO.getEntity().trim();
        if (!StringUtils.hasText(entity) && dto.getEntities() != null && !dto.getEntities().isEmpty()
                && dto.getEntities().get(0) != null) {
            entity = dto.getEntities().get(0).getId() == null ? "" : dto.getEntities().get(0).getId();
        }

        result.setIntent(intentName);
        result.setEntity(entity);
        result.setTransferred("转人工".equals(intentName));

        // 第三步：填槽检查（如果配置了槽位）
        SlotFillResultDTO slotResult = checkSlotFilling(dto, intentName);
        if (!slotResult.isComplete()) {
            result.setReply(slotResult.getPrompt());
            return result;
        }

        // 更新实体值（从填槽结果中获取）
        if (slotResult.getSlots() != null && !slotResult.getSlots().isEmpty()) {
            if (!StringUtils.hasText(entity) && slotResult.getSlots().containsKey("entity")) {
                entity = slotResult.getSlots().get("entity");
                result.setEntity(entity);
            }
        }

        // 第四步：意图分支路由（占位）
        String reply = routeByIntent(intentName, entity, dto, result);
        result.setReply(reply);
        if (tenantQuotaService != null) {
            tenantQuotaService.recordUsage(dto.getTenantCode(), 1);
        }

        // 第五步：消息落库（含附件），保证刷新页面后附件仍可回显
        persistMessages(dto, reply);
        return result;
    }

    /**
     * 持久化本轮对话的用户消息与 AI 回复。
     *
     * <p>附件随用户消息一起落库，前端拉取历史消息时按 {@code attachments} 字段渲染。
     * 落库失败不影响本次回复返回，仅记录告警。</p>
     */
    private void persistMessages(ChatDTO dto, String reply) {
        if (sessionFeign == null || !StringUtils.hasText(dto.getSessionId())) {
            return;
        }
        try {
            SessionDTO userMsg = new SessionDTO();
            userMsg.setSessionId(dto.getSessionId());
            userMsg.setMsgContent(dto.getMsg());
            userMsg.setSenderType(1);
            userMsg.setAttachments(dto.getAttachments());
            sessionFeign.saveMessage(userMsg);

            SessionDTO aiMsg = new SessionDTO();
            aiMsg.setSessionId(dto.getSessionId());
            aiMsg.setMsgContent(reply);
            aiMsg.setSenderType(2);
            sessionFeign.saveMessage(aiMsg);
        } catch (Exception e) {
            log.warn("对话消息落库失败 sessionId={}", dto.getSessionId(), e);
        }
    }

    /**
     * 意图识别（优先使用可配置意图服务，失败时回退到硬编码意图）
     */
    private IntentDTO recognizeIntent(ChatDTO dto) {
        try {
            String tenantCode = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode() : "default";
            return configurableIntentService.getIntent(dto.getMsg(), tenantCode);
        } catch (Exception e) {
            log.warn("可配置意图识别失败，回退到硬编码意图", e);
            return llmUtil.getIntent(dto.getMsg());
        }
    }

    /**
     * 填槽检查
     */
    private SlotFillResultDTO checkSlotFilling(ChatDTO dto, String intentName) {
        try {
            return slotFillingService.checkAndFillSlots(dto, intentName);
        } catch (Exception e) {
            log.warn("填槽检查失败", e);
            return SlotFillResultDTO.complete();
        }
    }

    /**
     * 按意图路由（占位：统一返回繁忙文案，不做任何分支）
     *
     * <p>TODO 后续实现四个分支：转人工、查物流/退款（开放工具）、投诉（建工单）、其余（知识库 RAG）。</p>
     */
    private String routeByIntent(String intentName, String entity, ChatDTO dto, ChatReplyDTO result) {
        log.warn("[占位] 意图路由未实现 intent={}，返回繁忙文案", intentName);
        return PromptConst.LLM_BUSY_REPLY;
    }

    /**
     * 解析行业提示词叠加片段（占位）
     *
     * <p>TODO 后续实现：按 packCode 加载行业人设与拒答片段，叠加到 RAG 查询串。</p>
     *
     * @param dto 对话请求
     * @return 占位返回 null，RAG 走通用 PromptConst
     */
    private String resolveIndustryOverlay(ChatDTO dto) {
        log.info("[占位] 解析行业提示词叠加片段 packCode={}", dto == null ? null : dto.getPackCode());
        return null;
    }
}
