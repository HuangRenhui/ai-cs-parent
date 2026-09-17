package com.ai.cs.base.mapper;

import com.ai.cs.base.entity.Statistics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据统计Mapper
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Mapper
public interface StatisticsMapper extends BaseMapper<Statistics> {

    /** 按天统计聊天会话数 */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM cs_chat_session " +
            "WHERE create_time >= #{startDate} AND create_time <= #{endDate} " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> countChatSessions(String startDate, String endDate);

    /** 按天统计工单数 */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM cs_work_order " +
            "WHERE create_time >= #{startDate} AND create_time <= #{endDate} " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> countWorkOrders(String startDate, String endDate);

    /** 按天统计客户新增数 */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM cs_customer " +
            "WHERE create_time >= #{startDate} AND create_time <= #{endDate} " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> countNewCustomers(String startDate, String endDate);

    /** 工单状态分布统计 */
    @Select("SELECT order_status AS status, COUNT(*) AS count FROM cs_work_order " +
            "GROUP BY order_status")
    List<Map<String, Object>> countWorkOrderByStatus();

    /** 工单类型分布统计 */
    @Select("SELECT order_type AS type, COUNT(*) AS count FROM cs_work_order " +
            "WHERE order_type IS NOT NULL GROUP BY order_type")
    List<Map<String, Object>> countWorkOrderByType();

    /** 坐席处理工单排行 */
    @Select("SELECT a.agent_name AS name, COUNT(wo.id) AS count " +
            "FROM cs_work_order wo LEFT JOIN cs_agent a ON wo.agent_id = a.id " +
            "WHERE wo.agent_id IS NOT NULL AND wo.agent_id > 0 " +
            "GROUP BY wo.agent_id ORDER BY count DESC LIMIT 10")
    List<Map<String, Object>> rankAgentByWorkOrders();
}
