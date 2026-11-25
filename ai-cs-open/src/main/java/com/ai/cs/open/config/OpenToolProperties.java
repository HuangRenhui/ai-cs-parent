package com.ai.cs.open.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 开放工具运行配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "open.tool")
public class OpenToolProperties {

    /**
     * 待确认记录的有效分钟数；0 或负数表示不过期。
     *
     * <p>超时用「读列表时惰性判断 + 条件更新」实现，不依赖定时任务——
     * 只有一个 job 才能发现超时的话，任务停摆时过期确认会被重新执行。</p>
     */
    private int confirmExpireMinutes = 30;

    /**
     * 幂等占位行的执行中断判定分钟数。超过该时长仍未回写的占位行置为 FAILED 并释放幂等键。
     */
    private int placeholderStaleMinutes = 10;
}
