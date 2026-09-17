package com.ai.cs.api.feign;

import com.ai.cs.api.feign.fallback.SessionFeignFallback;
import com.ai.cs.common.dto.SessionDTO;
import com.ai.cs.common.dto.SessionSnapshotDTO;
import com.ai.cs.common.dto.TransferResultDTO;
import com.ai.cs.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 会话服务 Feign 接口（由基础服务提供实现）：会话创建、消息落库、转人工、查询快照。
 */
@FeignClient(value = "ai-cs-base-service", url = "${feign.base-service.url:http://localhost:8084}", fallback = SessionFeignFallback.class)
public interface SessionFeign {

    /** 确保会话存在（不存在则创建） */
    @PostMapping("/session/ensure")
    Result<String> ensure(@RequestBody SessionDTO dto);

    /** 保存一条会话消息 */
    @PostMapping("/session/message")
    Result<String> saveMessage(@RequestBody SessionDTO dto);

    /** 把指定会话转为人工接待，成功时 data 带接入坐席工号/姓名 */
    @PutMapping("/session/{sessionId}/transfer")
    Result<TransferResultDTO> transfer(@PathVariable("sessionId") String sessionId);

    /** 按 sessionId 查询会话快照（不存在时 data 为 null） */
    @GetMapping("/session/{sessionId}/snapshot")
    Result<SessionSnapshotDTO> snapshot(@PathVariable("sessionId") String sessionId);

    /**
     * 把指定访客的匿名会话（customerId=0）合并到已登录客户。
     * 入参使用 {@link SessionDTO#getVisitorRef()} 与 {@link SessionDTO#getCustomerId()}。
     */
    @PostMapping("/session/merge-anonymous")
    Result<Integer> mergeAnonymous(@RequestBody SessionDTO dto);
}
