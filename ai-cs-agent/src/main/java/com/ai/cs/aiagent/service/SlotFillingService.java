package com.ai.cs.aiagent.service;

import com.ai.cs.api.feign.BaseServiceFeign;
import com.ai.cs.common.dto.ChatDTO;
import com.ai.cs.common.dto.SlotFillResultDTO;
import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 多轮填槽服务（占位）
 *
 * <p>TODO 后续实现：
 * <ol>
 *   <li>经 {@code BaseServiceFeign.getSlotsByIntent(tenantCode, intentCode)} 拉取该意图的槽位配置；</li>
 *   <li>从会话状态（{@code slot:state:<sessionId>}，TTL 30 分钟）读出已填槽位；</li>
 *   <li>从用户消息提取槽位值：优先按槽位 extractPrompt 调用模型提取，失败再用 validationRegex
 *       或按类型（phone/number/order_id）的默认正则兜底；</li>
 *   <li>逐个校验必填槽位：缺失或校验不过时回写状态并返回 incomplete（附追问/纠错话术），
 *       全部通过则返回 complete（携带槽位快照）；</li>
 *   <li>异常时返回 complete，避免阻塞主流程。</li>
 * </ol>
 * </p>
 *
 * <p>当前不拉配置、不调用模型、不读写槽位状态：{@link #checkAndFillSlots} 恒返回 complete
 * （等价于「该意图无需填槽」），因此多轮追问不会发生；{@link #clearSessionSlots} 也只记日志，
 * 不再删除 Redis 中的槽位状态。</p>
 *
 * @author huangrenhui
 * @date 2026-09-09
 */
@Slf4j
@Service
public class SlotFillingService {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private BaseServiceFeign baseServiceFeign;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis key前缀
     */
    private static final String SLOT_STATE_KEY_PREFIX = "slot:state:";

    /**
     * 槽位状态过期时间（30分钟）
     */
    private static final long SLOT_STATE_EXPIRE_MINUTES = 30;

    /**
     * 检查是否需要填槽（占位：恒返回 complete，不追问）
     *
     * @param dto        对话请求
     * @param intentCode 意图编码
     * @return complete 结果
     */
    public SlotFillResultDTO checkAndFillSlots(ChatDTO dto, String intentCode) {
        log.warn("[占位] 多轮填槽未实现 intentCode={} sessionId={}，直接返回 complete（不追问）",
                intentCode, dto == null ? null : dto.getSessionId());
        return SlotFillResultDTO.complete();
    }

    /**
     * 清除会话的槽位状态（占位：不读写槽位状态）
     *
     * <p>槽位状态（{@code slot:state:<sessionId>}）未落地，本方法不再删除 Redis key，只记日志。</p>
     *
     * @param sessionId 会话ID
     */
    public void clearSessionSlots(String sessionId) {
        log.info("[占位] 清除槽位状态未实现（槽位状态未落地） sessionId={}", sessionId);
    }
}
