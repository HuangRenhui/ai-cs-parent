// ai-cs-agent/src/main/java/com/ai/cs/aiagent/service/ConfigurableIntentService.java
package com.ai.cs.aiagent.service;

import com.ai.cs.api.feign.BaseServiceFeign;
import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.IntentConfigDTO;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.enums.IntentEnum;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 可配置意图识别服务。
 *
 * <p>先经 {@code BaseServiceFeign.getEnabledIntents(tenantCode)} 拉取租户自定义意图
 * （失败时回退硬编码 {@code IntentEnum}），据此构建分类器提示词（含意图清单与各自说明、
 * entity 抽取要求），调用 INTENT 类型模型后解析 JSON，并按
 * 「意图名 → 意图码 → 关键词」归一化，非法值回退「咨询」。</p>
 */
@Slf4j
@Service
public class ConfigurableIntentService {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private BaseServiceFeign baseServiceFeign;

    /**
     * 意图识别（配置优先版）。
     *
     * <p><b>与 {@code LlmUtil.getIntent} 的分工</b>：本类支持租户自定义意图，
     * 是首选实现；{@code LlmUtil} 是简化版（仅硬编码五类），
     * 由 {@code AiAgentService.recognizeIntent} 在<b>本类抛异常时</b>兜底调用。</p>
     *
     * <p><b>三级容错</b>（每级独立降级，互不牵连）：</p>
     * <ol>
     *   <li><b>意图配置拉取失败</b>（Feign 异常/返回空）→ 用空列表，提示词退回
     *       {@code PromptConst.INTENT_PROMPT} 的默认五类，<b>不影响识别</b></li>
     *   <li><b>模型调用失败</b> → 整个方法降级，返回「咨询」+ llmDegraded=true</li>
     *   <li><b>输出解析失败/意图无法归一化</b> → 同样降级为「咨询」</li>
     * </ol>
     *
     * <p><b>为什么降级后不直接回退「闲聊」</b>：{@code llmDegraded=true} 是给上层的
     * 信号，{@code AiAgentService} 见到它会返回「系统繁忙」话术。若此处擅自回退到
     * 闲聊模式，会把「模型故障」伪装成「正常闲聊回复」，监控与调用方都会失去感知。</p>
     *
     * @param userMsg    用户消息
     * @param tenantCode 租户编码
     * @return 识别结果；模型或配置不可用时降级为「咨询」（llmDegraded=true）
     */
    public IntentDTO getIntent(String userMsg, String tenantCode) {
        if (!StringUtils.hasText(userMsg)) {
            return fallbackConsult();
        }
        try {
            // 1. 拉租户启用的意图清单。失败返回空列表，不抛异常（对应容错第 1 级）
            List<IntentConfigDTO> intents = loadEnabledIntents(tenantCode);
            String intentList = buildIntentList(intents);

            // 2. 构建分类器提示词。
            //    意图清单非空 → 定点替换默认清单，支持租户自定义意图
            //    意图清单为空 → 直接用默认模板（硬编码五类）
            String prompt = StringUtils.hasText(intentList)
                    ? buildPromptWithIntents(intentList, userMsg)
                    : PromptConst.fill(PromptConst.INTENT_PROMPT, userMsg);

            List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", prompt));
            // 未注册 INTENT 模型时，ModelRouter 内部会回退到 yml 兜底配置
            String raw = modelRouter.chatForType(ModelTypeEnum.INTENT.getCode(), messages, null);

            // 3. 解析 + 归一化。intents 传入是为了让归一化优先匹配租户自定义意图
            return normalize(raw, intents);
        } catch (Exception e) {
            // 容错第 2/3 级：模型调用或解析异常，统一降级
            log.warn("可配置意图识别失败 tenantCode={}，降级为咨询", tenantCode, e);
            return fallbackConsult();
        }
    }

    /**
     * 拉取租户启用的意图配置。
     * <p>Feign 失败、返回码非 200、或列表为空时返回空列表，由调用方回退硬编码枚举。</p>
     */
    private List<IntentConfigDTO> loadEnabledIntents(String tenantCode) {
        try {
            Result<List<IntentConfigDTO>> result = baseServiceFeign.getEnabledIntents(tenantCode);
            if (result == null || result.getData() == null) {
                return List.of();
            }
            return result.getData().stream()
                    // 只取启用状态的意图（enabled=1），停用的不参与分类
                    .filter(item -> item != null && !Integer.valueOf(0).equals(item.getEnabled()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("拉取租户意图配置失败 tenantCode={}，回退硬编码意图", tenantCode, e);
            return List.of();
        }
    }

    /** 把意图配置拼成「中文名（说明）」的顿号清单，供提示词使用 */
    private String buildIntentList(List<IntentConfigDTO> intents) {
        if (intents == null || intents.isEmpty()) {
            return "";
        }
        return intents.stream()
                .map(item -> {
                    String name = firstNonBlank(item.getIntentName(), item.getIntentCode());
                    String desc = item.getDescription();
                    return StringUtils.hasText(desc) ? name + "（" + desc.trim() + "）" : name;
                })
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("、"));
    }

    /**
     * 用租户意图清单替换默认提示词中的意图列表行。
     *
     * <p><b>为什么用字符串切片而不是重建整个模板</b>：{@code PromptConst.INTENT_PROMPT}
     * 里除了意图清单，还有「只输出 JSON」的格式约束、entity 抽取要求等经过调优的内容。
     * 重建模板容易与常量失同步，所以这里只做<b>定点替换</b>——保留模板其余部分。</p>
     *
     * <p><b>切片逻辑</b>（以 {@code INTENT_PROMPT} 的结构为准）：</p>
     * <pre>
     *   你是智能客服的意图分类器。只输出 JSON：...
     *   intent 只能是以下之一：咨询、查物流、退款、投诉、转人工   ← 替换这一行
     *   entity：单号、手机号等关键信息...                        ← 从这里开始原样保留
     *   用户问题：
     *   %s                                                      ← 占位符换成用户消息
     * </pre>
     * <p>即拆成 <b>head（marker 之前）+ 新清单 + rest（从 entity 起）</b> 三段再拼接。</p>
     *
     * <p><b>容错设计</b>：</p>
     * <ul>
     *   <li>找不到 marker（模板被改）→ 直接拼一个自包含的新模板，而不是抛异常</li>
     *   <li>找不到换行符 → 退化为 marker 位置，保证 substring 不越界</li>
     *   <li>找不到 {@code entity：} → rest 取空串，只保留 head 与新清单</li>
     * </ul>
     *
     * @param intentList 租户意图的「名（说明）」顿号清单
     * @param userMsg    用户消息原文
     */
    private String buildPromptWithIntents(String intentList, String userMsg) {
        String base = PromptConst.INTENT_PROMPT;
        int marker = base.indexOf("intent 只能是以下之一");
        if (marker < 0) {
            // 模板结构变更时兜底：直接拼一个新模板，而不是抛异常
            return "你是智能客服的意图分类器。只输出 JSON：{\"intent\":\"<意图>\",\"entity\":\"<实体>\"}\n"
                    + "intent 只能是以下之一：" + intentList + "\n"
                    + "entity：单号、手机号等关键信息，没有则填空字符串。不要编造。\n"
                    + "用户问题：\n" + userMsg;
        }
        // marker 落在「intent 只能是以下之一」这几个字上，
        // 先跳过这一整行（找到行尾换行），避免把原清单的残余字符带进新提示词
        int markerLineEnd = base.indexOf('\n', marker);
        if (markerLineEnd < 0) {
            // 该行是模板最后一行，退化为 marker 位置
            markerLineEnd = marker;
        }
        String head = base.substring(0, marker);
        // rest 从 "entity：" 开始，即保留模板中意图清单之后的所有调优内容
        int tail = base.indexOf("entity：", markerLineEnd);
        String rest = tail < 0 ? "" : base.substring(tail);
        // rest 里仍含 %s 占位符，此处一并替换为真实用户消息
        return head + "intent 只能是以下之一：" + intentList + "\n" + rest.replace("%s", userMsg);
    }

    /**
     * 解析模型输出并归一化意图。
     * <p>归一化链：中文名精确匹配 → 英文枚举名匹配 → 租户意图码匹配 → 关键词兜底 → 咨询。</p>
     */
    private IntentDTO normalize(String raw, List<IntentConfigDTO> intents) {
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            return fallbackConsult();
        }
        String intentText = readStringField(json, "intent");
        String entity = readStringField(json, "entity");

        IntentDTO dto = new IntentDTO();
        dto.setIntent(resolveIntentName(intentText, intents));
        dto.setEntity(entity.trim());
        dto.setLlmDegraded(false);
        return dto;
    }

    /**
     * 把模型输出的意图文本解析为最终意图名。
     *
     * <p><b>为什么需要「归一化」</b>：模型输出的意图文本不可控——可能返回中文名
     * 「咨询」，也可能返回英文码 {@code CONSULT} 或 {@code consult}，甚至同义近义说法。
     * 下游 {@code routeByIntent} 用 {@code IntentEnum.fromName} 做 switch 分支，
     * 若这里不归一化，模型换个大小写就会走错分支。</p>
     *
     * <p><b>四级归一化链（按优先级）</b>：</p>
     * <ol>
     *   <li>空值 → 直接「咨询」</li>
     *   <li><b>租户自定义意图</b>：比对该租户配置的 intentName 与 intentCode
     *       （忽略大小写）。<b>必须放在枚举之前</b>——否则租户自定义的意图
     *       （如「查发票」）会被枚举兜底吞掉，扩展能力就失效了</li>
     *   <li>返回时取 intentName 优先、intentCode 兜底，保证返回的是「给人看的名字」</li>
     *   <li>都不匹配 → {@code IntentEnum.fromName}，其内部含关键词匹配 + 默认「咨询」</li>
     * </ol>
     *
     * @param intentText 模型输出的意图文本
     * @param intents    该租户启用的意图配置（可为空）
     * @return 归一化后的意图名；无法识别时为「咨询」
     */
    private String resolveIntentName(String intentText, List<IntentConfigDTO> intents) {
        if (!StringUtils.hasText(intentText)) {
            return IntentEnum.CONSULT.getName();
        }
        String text = intentText.trim();
        if (intents != null) {
            for (IntentConfigDTO item : intents) {
                // 模型可能输出意图名，也可能输出意图码，两者都比对。
                // safe() 防 null：租户可能只填了 code 没填 name，或反之
                if (text.equalsIgnoreCase(safe(item.getIntentName()))
                        || text.equalsIgnoreCase(safe(item.getIntentCode()))) {
                    return firstNonBlank(item.getIntentName(), item.getIntentCode());
                }
            }
        }
        // 硬编码枚举兜底：fromName 内部含关键词匹配与默认咨询
        return IntentEnum.fromName(text).getName();
    }

    /** 从模型输出中抠出第一个 JSON 对象（与大括号配对计数，兼容围栏与解释文字） */
    private String extractJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        int start = raw.indexOf('{');
        if (start < 0) {
            return "";
        }
        int depth = 0;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    /** 读取 JSON 字符串字段，兼容双引号与单引号 */
    private String readStringField(String json, String field) {
        int keyIdx = json.indexOf('"' + field + '"');
        if (keyIdx < 0) {
            keyIdx = json.indexOf('\'' + field + '\'');
        }
        if (keyIdx < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIdx);
        if (colon < 0) {
            return "";
        }
        int quoteStart = -1;
        char quote = 0;
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' || c == '\'') {
                quoteStart = i;
                quote = c;
                break;
            }
            if (c == ',' || c == '}') {
                return "";
            }
        }
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = json.indexOf(quote, quoteStart + 1);
        return quoteEnd < 0 ? "" : json.substring(quoteStart + 1, quoteEnd);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
    }

    /** 降级意图：统一回退为「咨询」，并打上 llmDegraded 标记供上层识别 */
    private IntentDTO fallbackConsult() {
        IntentDTO dto = new IntentDTO();
        dto.setIntent(IntentEnum.CONSULT.getName());
        dto.setEntity("");
        dto.setLlmDegraded(true);
        return dto;
    }
}
