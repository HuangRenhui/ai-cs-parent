package com.ai.cs.open.mapper;

import com.ai.cs.open.entity.InboundWebhook;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 入站 Webhook Mapper 接口。
 */
@Mapper
public interface InboundWebhookMapper extends BaseMapper<InboundWebhook> {
}
