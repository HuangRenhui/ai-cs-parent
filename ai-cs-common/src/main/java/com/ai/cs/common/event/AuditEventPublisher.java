package com.ai.cs.common.event;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 操作审计异步投递：写 Redis 列表，由 job 消费落库，避免写操作被审计同步拖慢。
 */
@Slf4j
@Component
public class AuditEventPublisher {

    /** 审计事件队列（左进右出） */
    public static final String QUEUE_KEY = "cs:audit:queue";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 投递一条审计事件。Redis 不可用时仅记日志，不阻断主流程。
     */
    public void publish(JSONObject event) {
        if (event == null) {
            return;
        }
        if (redisTemplate == null) {
            log.warn("审计事件未投递：Redis 不可用");
            return;
        }
        try {
            redisTemplate.opsForList().leftPush(QUEUE_KEY, event.toJSONString());
        } catch (Exception e) {
            log.warn("审计事件投递失败", e);
        }
    }
}
