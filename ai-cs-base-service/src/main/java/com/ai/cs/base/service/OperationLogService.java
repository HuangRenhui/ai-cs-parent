package com.ai.cs.base.service;

import com.ai.cs.base.entity.OperationLog;
import com.ai.cs.base.mapper.OperationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Service
public class OperationLogService extends ServiceImpl<OperationLogMapper, OperationLog> {

    /**
     * 分页查询操作日志
     */
    public Page<OperationLog> queryPage(int pageNum, int pageSize, Long userId, String module) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (module != null && !module.isBlank()) {
            wrapper.eq(OperationLog::getModule, module);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }
}
