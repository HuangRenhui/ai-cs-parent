package com.ai.cs.workorder.config;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flowable 工作流引擎配置
 * 当 flowable.enabled=true 时启用
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Configuration
@ConditionalOnProperty(name = "flowable.enabled", havingValue = "true", matchIfMissing = false)
public class FlowableConfig {

    /**
     * Flowable 流程引擎配置
     */
    @Bean
    public ProcessEngineConfiguration processEngineConfiguration(DataSource dataSource) {
        StandaloneProcessEngineConfiguration config = new StandaloneProcessEngineConfiguration();
        config.setDataSource(dataSource);
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        return config;
    }

    /**
     * Flowable 流程引擎
     */
    @Bean
    public ProcessEngine processEngine(ProcessEngineConfiguration config) {
        return config.buildProcessEngine();
    }
}
