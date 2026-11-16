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
}
