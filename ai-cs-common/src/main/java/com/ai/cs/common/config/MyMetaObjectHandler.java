package com.ai.cs.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充配置
 *
 * @author huangrenhui
 * @date 2026/6/11 18:03
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    /**
     * 新增时填充：createTime / updateTime 均取当前时间。
     * strictInsertFill 仅在实体字段为 null 时填充，不覆盖调用方显式赋的值。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /** 更新时填充：只刷新 updateTime */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
