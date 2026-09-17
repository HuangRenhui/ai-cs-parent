package com.ai.cs.knowledge.util;

import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识库侧 LLM 入口（占位）
 *
 * <p>TODO 后续实现：底层统一走 {@link ModelRouter}（未登记对话模型时回退历史配置，登记后支持本地/在线切换与故障转移）。
 * 需保留 {@code call} / {@code callWithSystem} / {@code callWithMessages} 三个签名与 <b>IOException 异常语义</b>：
 * 提示词为空或模型调用失败（{@code ModelCallException}）时统一包装为 {@link IOException}，
 * 供上层 Service 沿用异常分支；{@code callWithMessages} 还要把宽松的 Map 归一化为 role/content 字符串对（role 缺省 user）。</p>
 *
 * <p>当前不调用模型：三个方法一律抛 {@link IOException}（占位），上层既有的异常降级分支照常生效。</p>
 */
@Slf4j
@Component
public class LlmClient {

    @Resource
    private ModelRouter modelRouter;

    /**
     * 单轮对话调用（占位：直接抛 IOException）
     *
     * @param prompt 用户提示词
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String call(String prompt) throws IOException {
        log.warn("[占位] 知识库 LLM 单轮调用未实现");
        throw new IOException("调用大模型异常: 知识库 LLM 调用为占位实现");
    }

    /**
     * 带系统提示词的对话调用（占位：直接抛 IOException）
     *
     * @param systemPrompt 系统提示词（可为空）
     * @param userPrompt   用户提示词
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String callWithSystem(String systemPrompt, String userPrompt) throws IOException {
        log.warn("[占位] 知识库 LLM 系统提示词调用未实现");
        throw new IOException("调用大模型异常: 知识库 LLM 调用为占位实现");
    }

    /**
     * 多消息列表对话调用（占位：直接抛 IOException）
     *
     * @param messages 消息列表（每条含 role、content 键）
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String callWithMessages(List<Map<String, Object>> messages) throws IOException {
        log.warn("[占位] 知识库 LLM 多消息调用未实现");
        throw new IOException("调用大模型异常: 知识库 LLM 调用为占位实现");
    }
}
