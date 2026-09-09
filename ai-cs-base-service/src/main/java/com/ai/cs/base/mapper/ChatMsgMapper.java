package com.ai.cs.base.mapper;

import com.ai.cs.base.entity.ChatMsg;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper
 *
 * @author huangrenhui
 * @date 2026/6/11 18:13
 */
@Mapper
public interface ChatMsgMapper extends BaseMapper<ChatMsg> {
}
