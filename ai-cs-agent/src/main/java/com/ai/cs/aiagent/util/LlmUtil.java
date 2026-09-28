// ai-cs-agent/src/main/java/com/ai/cs/aiagent/util/LlmUtil.java
package com.ai.cs.aiagent.util;

import com.ai.cs.common.constant.PromptConst;
import com.ai.cs.common.dto.IntentDTO;
import com.ai.cs.common.enums.IntentEnum;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 意图识别与闲聊回复。
 *
 * <p>意图识别把用户消息填入 {@code PromptConst.INTENT_PROMPT}，经 {@code ModelRouter} 调用 INTENT
 * 类型模型，从输出中提取 JSON 主体（兼容 ```json 代码块与夹杂解释文字），再用
 * {@code IntentEnum.fromName} 归一化非法意图名。模型不可用时降级为「咨询」且置
 * {@code llmDegraded=true}，由调用方 {@code AiAgentService.chatDetail} 转成繁忙话术。</p>
 */
@Slf4j
@Component
public class LlmUtil {

    /** JSON 对象在文本中的起止字符，用于从夹杂解释的输出中抠出 JSON 主体 */
    private static final char JSON_START = '{';
    private static final char JSON_END = '}';

    @Resource
    private ModelRouter modelRouter;

    /**
     * 意图识别：调用大模型分类，解析 JSON 结果。
     *
     * <p><b>降级策略（本方法的核心设计）</b>：整条链路包裹在 try-catch 中，任何异常
     * 都收敛为「咨询」+ {@code llmDegraded=true}，<b>绝不向上抛</b>。原因是意图识别
     * 只是路由的前置判断，若让它中断整个对话，用户体验会从「答得不准」劣化为
     * 「完全没有响应」——两害相权取其轻。</p>
     *
     * <p>注意 {@code llmDegraded=true} 是给上层的关键信号：{@code AiAgentService.chatDetail}
     * 见到该标记会直接返回「系统繁忙」话术，而<b>不会</b>用降级后的「咨询」去做 RAG 检索，
     * 避免模型故障时还硬答一通、把故障伪装成正常回答。</p>
     *
     * @param userMsg 用户消息原文
     * @return 识别结果；失败时为降级结果（llmDegraded=true）
     */
    public IntentDTO getIntent(String userMsg) {
        if (!StringUtils.hasText(userMsg)) {
            return fallbackConsult();
        }
        try {
            // 1. 组装提示词。用 PromptConst.fill 而非 String.format：
            //    用户文本里若带 % 号，String.format 会抛 MissingFormatArgumentException
            String prompt = PromptConst.fill(PromptConst.INTENT_PROMPT, userMsg);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt));

            // 2. 走路由器。type=INTENT 可与主对话模型解耦：
            //    已注册 INTENT 模型则用它，未注册时 ModelRouter 内部回退到 yml 兜底配置。
            //    意图识别是高频短任务，独立配一个小模型能显著降本
            String raw = modelRouter.chatForType(ModelTypeEnum.INTENT.getCode(), messages, null);

            // 3. 解析。模型常返回 ```json 包裹或夹带解释文字，parseIntent 内部已做容错
            IntentDTO dto = parseIntent(raw);
            if (dto != null) {
                return dto;
            }
            // 模型有返回但抠不出 JSON：可能是输出了纯自然语言
            log.warn("意图识别输出无法解析，降级为咨询 raw={}", abbreviate(raw));
        } catch (Exception e) {
            // ModelRouter 在「候选全失败」时会抛异常（这是有意设计：不静默换兜底模型），
            // 这里统一降级，保证对话链路不断
            log.warn("意图识别失败，降级为咨询 userMsg={}", abbreviate(userMsg), e);
        }
        return fallbackConsult();
    }

    /**
     * 闲聊回复：走 CHAT_PROMPT，两个 %s 依次为对话历史与用户问题。
     *
     * @param userMsg 用户消息原文
     * @param history 对话历史文本（可空）
     * @return 模型回复；失败时返回繁忙兜底文案
     */
    public String chatReply(String userMsg, String history) {
        if (!StringUtils.hasText(userMsg)) {
            return PromptConst.LLM_BUSY_REPLY;
        }
        try {
            String prompt = PromptConst.fill(
                    PromptConst.CHAT_PROMPT,
                    StringUtils.hasText(history) ? history : "（无）",
                    userMsg);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", prompt));
            String reply = modelRouter.chat(messages, null);
            return StringUtils.hasText(reply) ? reply.trim() : PromptConst.LLM_BUSY_REPLY;
        } catch (Exception e) {
            log.warn("闲聊回复失败，返回繁忙文案", e);
            return PromptConst.LLM_BUSY_REPLY;
        }
    }

    /**
     * 解析意图模型输出为 DTO。
     *
     * <p>兼容三种常见输出形态：纯 JSON、```json 围栏包裹、JSON 前后夹带解释文字。
     * 任一字段非法都回退默认值（intent→咨询、entity→空串），只有整体无法解析为 JSON 时才返回 null。</p>
     *
     * @return 解析结果；无法抠出 JSON 时返回 null，由调用方决定降级
     */
    private IntentDTO parseIntent(String raw) {
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        String intentText = readStringField(json, "intent");
        String entity = readStringField(json, "entity");

        IntentDTO dto = new IntentDTO();
        // fromName 内部已做「精确匹配→关键词兜底→默认咨询」，无需在这里再判空
        dto.setIntent(IntentEnum.fromName(intentText).getName());
        dto.setEntity(entity.trim());
        dto.setLlmDegraded(false);
        return dto;
    }

    /**
     * 从模型输出中抠出第一个 JSON 对象。
     *
     * <p><b>为什么需要它</b>：模型的实际输出往往不是干净 JSON，常见三种形态——</p>
     * <pre>
     *   ① 纯 JSON          : {"intent":"咨询","entity":""}
     *   ② 代码块包裹       : ```json\n{"intent":"咨询"}\n```
     *   ③ 夹带解释文字     : 好的，分析结果如下：{"intent":"咨询"} 希望有帮助
     * </pre>
     * <p><b>算法</b>：找到第一个 <code>{</code> 后，用 depth 做括号配对计数，
     * depth 归零处即为完整对象的右边界。这样天然跳过 ② 的围栏和 ③ 的噪声文字，
     * 且能正确处理嵌套（如 <code>{"a":{"b":1}}</code>，中途 depth 会到 2）。
     * 相比正则更稳——正则难以表达嵌套结构。</p>
     *
     * <p><b>局限性</b>：不识别字符串字面量内的大括号，例如
     * <code>{"entity":"订单{123}"}</code> 会被误判为提前闭合。但意图识别场景下
     * entity 是单号/手机号，几乎不会含大括号，故可接受。</p>
     *
     * @param raw 模型原始输出
     * @return 抠出的 JSON 子串；无 <code>{</code> 或括号未闭合时返回空串
     */
    private String extractJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        int start = raw.indexOf(JSON_START);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == JSON_START) {
                depth++;
            } else if (c == JSON_END) {
                depth--;
                if (depth == 0) {
                    // 配对归零：当前位置即对象结尾
                    return raw.substring(start, i + 1);
                }
            }
        }
        // 大括号未闭合（输出被截断等），视为解析失败
        return "";
    }

    /**
     * 读取 JSON 中的字符串字段值，对模型的「脏输出」保持宽容。
     *
     * <p><b>为什么不直接用 Jackson</b>：模型输出经常不完全合法（单引号、缺逗号、
     * 值未加引号），严格解析器会直接抛异常导致整个意图识别降级。
     * 手写扫描只关心「拿到目标字段的值」，对无关部分的语法错误免疫。</p>
     *
     * <p><b>扫描步骤</b>：</p>
     * <ol>
     *   <li>定位 key（先试双引号 <code>"field"</code>，再试单引号）</li>
     *   <li>找 key 之后的第一个冒号</li>
     *   <li>从冒号后逐字符前进，跳过空格等，遇到引号即为值的起点</li>
     *   <li>用同种引号找到配对终点，取中间内容</li>
     * </ol>
     *
     * <p><b>关键边界处理</b>：第 3 步若在遇到引号之前先碰到 <code>,</code> 或
     * <code>}</code>，说明该字段的值不是字符串（如 <code>{"entity":null}</code>
     * 或 <code>{"entity":123}</code>），此时立即返回空串，避免把后续字段的值误取过来。</p>
     *
     * @param json  已抠出的 JSON 子串
     * @param field 字段名（不含引号）
     * @return 字段值；字段缺失、值非字符串、或引号不配对时返回空串
     */
    private String readStringField(String json, String field) {
        int keyIdx = json.indexOf('"' + field + '"');
        if (keyIdx < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIdx);
        if (colon < 0) {
            return "";
        }
        // 从冒号后找第一个引号对；兼容 " 与 ' 两种引号
        int quoteStart = -1;
        char quote = 0;
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' || c == '\'') {
                quoteStart = i;
                quote = c;
                break;
            }
            // 先遇到分隔符说明该字段无字符串值（null / 数字 / 布尔），直接判定为空
            if (c == ',' || c == '}') {
                return "";
            }
        }
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = json.indexOf(quote, quoteStart + 1);
        if (quoteEnd < 0) {
            // 只有开引号没有闭引号（输出被截断），视为无值
            return "";
        }
        return json.substring(quoteStart + 1, quoteEnd);
    }

    /** 构造降级意图：统一回退为「咨询」，并打上 llmDegraded 标记供上层识别 */
    private IntentDTO fallbackConsult() {
        IntentDTO dto = new IntentDTO();
        dto.setIntent(IntentEnum.CONSULT.getName());
        dto.setEntity("");
        dto.setLlmDegraded(true);
        return dto;
    }

    /** 日志截断，避免长文本把日志刷爆 */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
