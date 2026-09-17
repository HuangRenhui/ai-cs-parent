package com.ai.cs.workorder.mapper;

import com.ai.cs.workorder.entity.WorkOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单 Mapper
 *
 * @author huangrenhui
 * @date 2026/6/11 18:16
 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
}