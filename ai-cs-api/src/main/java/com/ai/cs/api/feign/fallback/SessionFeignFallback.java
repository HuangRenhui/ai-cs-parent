package com.ai.cs.api.feign.fallback;

import com.ai.cs.api.feign.SessionFeign;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.dto.SessionSnapshotDTO;
import com.ai.cs.common.dto.TransferResultDTO;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会话服务 Feign 降级：基础服务不可用时返回 503，调用方记日志但不阻断聊天主流程。
 */
@Slf4j
@Component
public class SessionFeignFallback implements SessionFeign {

    /** 降级：会话创建/确保失败，返回 503 */
    @Override
    public Result<String> ensure(SessionDTO dto) {
        log.error("会话确保失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用");
    }

    /** 降级：消息落库失败，返回 503（消息丢失仅影响历史记录，不影响本次回复） */
    @Override
    public Result<String> saveMessage(SessionDTO dto) {
        log.error("会话消息保存失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用");
    }

    @Override
    public Result<String> saveTurn(java.util.List<SessionDTO> messages) {
        log.error("对话落库失败，触发熔断降级");
        return Result.fail(503, "基础服务暂时不可用");
    }

    /**
     * 转人工兜底：会话服务不可达时返回失败而非成功，
     * 避免上层误判为「已转接」。
     */
    @Override
    public Result<TransferResultDTO> transfer(String sessionId) {
        // 必须返回 fail 或 data=null，绝不能造一个「有坐席」的假数据，
        // 否则上层会误判为「已转接」，向用户谎称已接入人工
        log.error("转人工失败，触发熔断降级 sessionId={}", sessionId);
        return Result.fail(503, "会话服务暂不可用");
    }

    /** 降级：会话快照查询失败，返回空数据由调用方按「会话不存在」处理 */
    @Override
    public Result<SessionSnapshotDTO> snapshot(String sessionId) {
        log.error("会话快照查询失败，触发熔断降级 sessionId={}", sessionId);
        return Result.fail(503, "基础服务暂时不可用");
    }

    /** 降级：匿名会话合并失败，返回 503，Widget 登录后历史会话可能仍挂在匿名客户上 */
    @Override
    public Result<Integer> mergeAnonymous(SessionDTO dto) {
        log.error("匿名会话合并失败，触发熔断降级 visitorRef={}", dto == null ? null : dto.getVisitorRef());
        return Result.fail(503, "基础服务暂时不可用");
    }
}
