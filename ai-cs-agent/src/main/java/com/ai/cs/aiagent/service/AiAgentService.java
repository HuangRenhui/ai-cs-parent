package com.ai.cs.aiagent.service;

import cn.hutool.core.lang.UUID;
import com.ai.cs.aiagent.util.LlmUtil;
import com.ai.cs.api.feign.KnowledgeFeign;
import com.ai.cs.api.feign.OpenToolFeign;
import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.api.feign.WorkOrderFeign;
import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.*;
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
 * <p><b>保留已实现</b>：入参校验（非空、长度 1~2000）、租户日配额拦截、
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
     * <p>校验与安全检查已实现；意图识别、填槽与路由的下游能力已为占位实现，
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
        // 此处先置 false，真正的「已转接」由 routeToAgent 在分配成功后才置 true。
        // 若在意图层就置 true，routeToAgent 失败时前端仍会渲染接待卡，
        // 等于向用户谎称已接入人工——正是 routeToAgent 注释中明确禁止的行为
        result.setTransferred(false);

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
        persistMessages(dto, result);
        return result;
    }

    /**
     * 同一事务写入用户消息与 AI 回复。落库失败时在回复中说明，并标记 historySaved=false。
     */
    private void persistMessages(ChatDTO dto, ChatReplyDTO result) {
        String reply = result.getReply();
        if (sessionFeign == null || !StringUtils.hasText(dto.getSessionId())) {
            result.setHistorySaved(false);
            return;
        }
        try {
            SessionDTO userMsg = new SessionDTO();
            userMsg.setSessionId(dto.getSessionId());
            userMsg.setMsgContent(dto.getMsg());
            userMsg.setSenderType(1);
            userMsg.setAttachments(dto.getAttachments());
            userMsg.setCustomerId(dto.getCustomerId());
            userMsg.setVisitorRef(dto.getVisitorRef());

            SessionDTO aiMsg = new SessionDTO();
            aiMsg.setSessionId(dto.getSessionId());
            aiMsg.setMsgContent(reply);
            aiMsg.setSenderType(2);

            Result<String> saved = sessionFeign.saveTurn(java.util.List.of(userMsg, aiMsg));
            if (saved == null || !saved.isOk()) {
                markHistoryMissing(result, reply, dto.getSessionId(), null);
            }
        } catch (Exception e) {
            markHistoryMissing(result, reply, dto.getSessionId(), e);
        }
    }

    private void markHistoryMissing(ChatReplyDTO result, String reply, String sessionId, Exception e) {
        log.error("对话消息落库失败 sessionId={}", sessionId, e);
        result.setHistorySaved(false);
        String note = "（本次对话未能写入会话记录）";
        if (reply == null || !reply.contains(note)) {
            result.setReply((reply == null ? "" : reply) + "\n" + note);
        }
    }

    /**
     * 意图识别（双路径，可配置优先）。
     *
     * <p><b>为什么是两层而不是一层</b>：{@code ConfigurableIntentService} 支持租户自定义
     * 意图，但引入了 Feign 依赖（拉意图配置），比 {@code LlmUtil} 多一个可能失败的环节。
     * 这里再加一层兜底，确保「配置服务抖动」不会导致整个对话不可用。</p>
     *
     * <p><b>注意两层的语义差别</b>：</p>
     * <ul>
     *   <li>本方法 catch 的是<b>意外异常</b>（如 Feign 熔断、NPE），此时降级到只能识别
     *       硬编码五类的 {@code LlmUtil}，是「能力降级但仍有结果」</li>
     *   <li>而 {@code ConfigurableIntentService} 内部对<b>模型调用失败</b>已自行处理为
     *       {@code llmDegraded=true}，正常返回（不抛异常），因此不会走到这里</li>
     * </ul>
     * <p>换言之：<b>能拿到 DTO 就说明意图判断过程没崩</b>，是否可靠由 {@code llmDegraded} 字段表达。</p>
     *
     * @param dto 对话入参
     * @return 意图识别结果；最差情况为「咨询」+ llmDegraded=true
     */
    private IntentDTO recognizeIntent(ChatDTO dto) {
        try {
            // tenantCode 兜底为 default：单租户部署时前端可能不传该字段
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
            log.warn("填槽检查失败，中止本轮路由", e);
            return SlotFillResultDTO.incomplete("slot", PromptConst.LLM_BUSY_REPLY);
        }
    }

    /**
     * 按意图路由到对应业务分支。
     *
     * <p><b>为什么再次调用 {@code IntentEnum.fromName}</b>：{@code intentName} 来自识别层，
     * 可能是租户自定义的名称（如「查发票」），枚举里并不存在。这里重新归一化一次，
     * 让未知意图自然落到 default 分支，避免下游用字符串比较时漏判。</p>
     *
     * <p><b>四个分支的当前状态</b>：</p>
     * <ul>
     *   <li>{@link #routeToKnowledge} —— <b>已实现</b>，依赖知识库 RAG（已打通）</li>
     *   <li>{@link #routeToAgent} —— 占位，待 P0-序4「坐席分配」</li>
     *   <li>{@link #routeToTool} —— 占位，待 P0-序5/6「开放工具与幂等」</li>
     *   <li>{@link #routeToWorkOrder} —— 占位，待建单能力接入</li>
     * </ul>
     *
     * <p><b>兜底策略</b>：{@code CONSULT} 与 {@code default} 合并，都走 RAG。
     * 这是有意设计——模型分类偶发不准时（返回了未知意图），RAG 是最安全且有价值的
     * 落点：答不上来会明确说「暂无资料」，而不会像其他分支那样返回「功能暂不可用」。</p>
     *
     * <p><b>外层 try-catch</b>：单个分支抛异常不影响整体，统一转繁忙文案，
     * 避免把内部异常堆栈暴露给用户。</p>
     *
     * @param intentName 意图名（可能为租户自定义，未必能匹配枚举）
     * @param entity     从用户消息中抽取的实体（单号/手机号等，可为空串）
     */
    private String routeByIntent(String intentName, String entity, ChatDTO dto, ChatReplyDTO result) {
        IntentEnum intent = IntentEnum.fromName(intentName);
        try {
            switch (intent) {
                case TO_AGENT:
                    return routeToAgent(dto, result);
                case QUERY_LOGISTICS:
                case REFUND:
                    // 查物流与退款共用工具分支：都需走开放工具调用 + 幂等控制
                    return routeToTool(intent, entity, dto, result);
                case COMPLAINT:
                    return routeToWorkOrder(entity, dto, result);
                case CONSULT:
                default:
                    // 兜底也走 RAG，而不是繁忙文案：模型分类偶发不准时，
                    // 知识库问答是最安全且最有价值的兜底
                    return routeToKnowledge(dto, result);
            }
        } catch (Exception e) {
            log.warn("意图路由异常 intent={} sessionId={}", intentName, dto.getSessionId(), e);
            return PromptConst.LLM_BUSY_REPLY;
        }
    }

    /**
     * 转人工分支：调会话服务分配坐席，成功时回填接待卡信息。
     *
     * <p><b>核心约束——失败必须如实告知</b>：只有 {@code SessionFeign.transfer}
     * 返回成功且 {@code agentId} 非空，才回「已接入」话术。任何环节失败都回
     * {@code TRANSFER_FAIL_REPLY}，<b>绝不假装已转接</b>。</p>
     *
     * <p>原因：转人工是用户在机器人答不上来时的兜底诉求。若系统实际没接入却提示
     * 「已转接，请稍候」，用户会一直在页面上等一个永远不会来的客服——
     * 这比直接说「暂时无法接入」的伤害大得多。</p>
     *
     * <p><b>三种情况的话术区分</b>：</p>
     * <ul>
     *   <li>成功且分到坐席 → 「已为您接入人工客服 工号 A002 张三」</li>
     *   <li>成功但无在线坐席 → 排队提示（agentId 为空）</li>
     *   <li>调用失败 → {@code TRANSFER_FAIL_REPLY}</li>
     * </ul>
     */
    private String routeToAgent(ChatDTO dto, ChatReplyDTO result) {
        if (sessionFeign == null || !StringUtils.hasText(dto.getSessionId())) {
            log.warn("转人工失败：会话服务不可用或缺少 sessionId sessionId={}", dto.getSessionId());
            return PromptConst.TRANSFER_FAIL_REPLY;
        }
        try {
            Result<TransferResultDTO> response = sessionFeign.transfer(dto.getSessionId());
            TransferResultDTO transfer = response == null ? null : response.getData();
            if (transfer == null) {
                log.warn("转人工失败：会话服务返回空 sessionId={}", dto.getSessionId());
                return PromptConst.TRANSFER_FAIL_REPLY;
            }
            // 回填接待卡信息，供前端展示坐席工号/姓名（ChatPage 的 transferredAgent）
            result.setTransferred(true);
            result.setAgentId(transfer.getAgentId());
            result.setAgentNo(transfer.getAgentNo());
            result.setAgentName(transfer.getAgentName());
            if (transfer.getAgentId() == null) {
                // 已转人工但暂无在线坐席：会话状态已改为人工接待，只是尚未指派具体坐席。
                // transferred 仍为 true（前端显示接待卡），agentId 为空由前端渲染「排队中」，
                // 而不是谎称已接入具体某位客服
                return "已为您转接人工客服，当前坐席繁忙，请稍候。";
            }
            return "已为您接入人工客服。工号 " + transfer.getAgentNo()
                    + " " + transfer.getAgentName() + " 正在为您服务，请简要说明问题。";
        } catch (Exception e) {
            log.warn("转人工调用失败 sessionId={}", dto.getSessionId(), e);
            return PromptConst.TRANSFER_FAIL_REPLY;
        }
    }

    /**
     * 工具分支：查物流 / 退款。
     *
     * <p><b>两条安全约束</b>（来自 [详细设计] 与幂等设计）：</p>
     * <ol>
     *   <li><b>退款等高危操作必须先确认</b>：用户消息含「确认」才置 confirmed=true，
     *       否则返回确认提示，形成「确认循环」——防止误触导致真实退款</li>
     *   <li><b>幂等键 = sessionId:意图:实体</b>：用户重试或网络重发时，
     *       开放层凭唯一索引挡住重复执行，不会退两次款</li>
     * </ol>
     */
    private String routeToTool(IntentEnum intent, String entity, ChatDTO dto, ChatReplyDTO result) {
        ToolInvokeDTO toolDto = new ToolInvokeDTO();
        toolDto.setSessionId(dto.getSessionId());
        // 工具按「意图绑定」检索：注册表里工具可能只绑了意图而未指定工具名
        toolDto.setIntentBind(intent.getName());
        toolDto.setEntityId(entity);
        toolDto.setEntityType("order");
        // 幂等键：同一会话 + 同一意图 + 同一实体视为同一次操作
        toolDto.setIdempotencyKey(dto.getSessionId() + ":" + intent.getName() + ":" + entity);
        // 租户编码用于连接器出站白名单校验，避免跨租户调用对方系统
        toolDto.setTenantCode(dto.getTenantCode());

        // 高危操作需用户明确确认
        boolean risky = intent == IntentEnum.REFUND;
        boolean confirmed = StringUtils.hasText(dto.getMsg()) && dto.getMsg().contains("确认");
        toolDto.setConfirmed(!risky || confirmed);
        try {
            Result<ToolInvokeResultDTO> response = openToolFeign.invoke(toolDto);
            ToolInvokeResultDTO tool = response == null ? null : response.getData();
            if (tool == null) {
                return PromptConst.TOOL_BUSY_REPLY;
            }
            // 需确认但用户还没确认：返回确认提示，等下一轮
            if (!tool.isSuccess()) {
                if (StringUtils.hasText(tool.getOutput())) {
                    return tool.getOutput();
                }
                return PromptConst.TOOL_BUSY_REPLY;
            }
            return StringUtils.hasText(tool.getOutput()) ? tool.getOutput() : PromptConst.TOOL_BUSY_REPLY;
        } catch (Exception e) {
            log.warn("工具调用失败 sessionId={} intent={}", dto.getSessionId(), intent.getName(), e);
            return PromptConst.TOOL_BUSY_REPLY;
        }
    }

    /**
     * 【占位】投诉分支。
     * <p>待建单能力接好后补齐：经 {@code WorkOrderFeign.createOrder} 建单并回报单号。</p>
     */
    private String routeToWorkOrder(String entity, ChatDTO dto, ChatReplyDTO result) {
        log.info("[占位] 工单分支未实现 entity={}", entity);
        return PromptConst.TOOL_BUSY_REPLY;
    }

    /**
     * 咨询分支：调用知识库 RAG，按三态返回对应话术。
     *
     * <p><b>三态的核心区别（最容易被写错的地方）</b>——{@code MISS} 与
     * {@code UNAVAILABLE} 必须区别对待，否则会把「系统故障」伪装成「知识库没资料」，
     * 让用户以为是自己问错了、反复换问法，而实际是服务挂了：</p>
     *
     * <table border="1">
     *   <tr><th>状态</th><th>含义</th><th>返回话术</th></tr>
     *   <tr><td>HIT</td><td>检索到相关知识</td><td>知识回复 + citations（前端展示「参考资料」）</td></tr>
     *   <tr><td>MISS</td><td>知识库确实没有</td><td>换问法 / 转人工提示</td></tr>
     *   <tr><td>UNAVAILABLE</td><td>知识服务或模型异常</td><td>「系统繁忙，请稍后再试」</td></tr>
     * </table>
     *
     * <p>另外，{@code knowledgeStatus} 会回填到 {@code result} 并随响应返回前端，
     * 便于前端做差异化展示（如 HIT 才渲染引用区）。</p>
     *
     * <p><b>异常兜底</b>：整个 Feign 调用包在 try-catch 中，异常一律视为 UNAVAILABLE
     * （而非 MISS）——同样是「不谎称没找到」原则。</p>
     */
    private String routeToKnowledge(ChatDTO dto, ChatReplyDTO result) {
        // 纯附件消息没有文本可检索。这里标 MISS 是因为「确实无从检索」，
        // 而非服务故障；话术引导用户补充描述
        if (!StringUtils.hasText(dto.getMsg())) {
            result.setKnowledgeStatus(RagSearchResultDTO.MISS);
            return "请补充一下您的问题描述，或直接联系人工客服。";
        }
        try {
            String tenantCode = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode() : "default";
            // 行业人设叠加：当前 resolveIndustryOverlay 为占位（返回 null），
            // 传 null 时知识服务走通用 PromptConst，不影响功能
            String industryPrompt = resolveIndustryOverlay(dto);
            Result<RagSearchResultDTO> response = knowledgeFeign.ragSearch(
                    dto.getMsg(), tenantCode, dto.getSessionId(), industryPrompt
            );
            RagSearchResultDTO rag = response == null ? null : response.getData();
            // 拿不到有效响应（Feign 返回 null / body 缺 status）→ 按服务不可用处理
            if (rag == null || !StringUtils.hasText(rag.getStatus())) {
                result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
                return PromptConst.LLM_BUSY_REPLY;
            }
            result.setKnowledgeStatus(rag.getStatus());
            if (RagSearchResultDTO.HIT.equals(rag.getStatus())) {
                // 命中：带上引用来源，前端可展示「参考资料」
                if (rag.getCitations() != null) {
                    result.setCitations(rag.getCitations());
                }
                // reply 为空属知识库数据异常，退化为「暂无资料」话术而非空白回复
                return StringUtils.hasText(rag.getReply()) ? rag.getReply() : PromptConst.NO_KNOWLEDGE_REPLY;
            }
            if (RagSearchResultDTO.MISS.equals(rag.getStatus())) {
                // 未命中：用知识服务给的兜底话术（通常是 PromptConst.NO_KNOWLEDGE_REPLY）
                return StringUtils.hasText(rag.getReply()) ? rag.getReply() : PromptConst.NO_KNOWLEDGE_REPLY;
            }
            // UNAVAILABLE：上游故障，走繁忙文案，不谎称「没找到」。
            // error 由知识服务透传，便于排查是向量库还是模型的问题
            log.warn("知识库不可用 sessionId={} error={}", dto.getSessionId(),
                    rag.getError() == null ? "" : rag.getError());
            return PromptConst.LLM_BUSY_REPLY;
        } catch (Exception e) {
            log.warn("知识库检索失败 sessionId={}", dto.getSessionId(), e);
            result.setKnowledgeStatus(RagSearchResultDTO.UNAVAILABLE);
            return PromptConst.LLM_BUSY_REPLY;
        }
    }

    /**
     * 【占位】解析行业提示词叠加片段。
     *
     * <p>TODO 待 P1-序11 补齐：按 packCode 经 {@code IndustryPromptPackService.getOverlayPrompt}
     * 加载行业人设与拒答片段，叠加到 RAG 查询串。</p>
     *
     * @return 当前返回 null，知识服务走通用 PromptConst，不影响功能
     */
    private String resolveIndustryOverlay(ChatDTO dto) {
        if (industryPromptPackService == null || dto == null
                || !StringUtils.hasText(dto.getPackCode())) {
            return null;
        }
        try {
            // 该服务当前为占位（getOverlayPrompt 返回 null），接通后无需改动此处
            return industryPromptPackService.getOverlayPrompt(dto.getPackCode());
        } catch (Exception e) {
            log.warn("加载行业提示词失败 packCode={}", dto.getPackCode(), e);
            return null;
        }
    }
}
