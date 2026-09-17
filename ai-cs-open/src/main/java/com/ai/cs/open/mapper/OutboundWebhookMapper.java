package com.ai.cs.open.mapper;

import com.ai.cs.open.entity.OutboundWebhook;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出站 Webhook Mapper 接口。
 */
@Mapper
public interface OutboundWebhookMapper extends BaseMapper<OutboundWebhook> {
}
