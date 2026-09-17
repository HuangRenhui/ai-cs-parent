package com.ai.cs.base.mapper;

import com.ai.cs.base.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户Mapper
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 查询用户拥有的角色编码（仅启用且未删除的角色） */
    @Select("SELECT DISTINCT r.role_code FROM cs_role r " +
            "INNER JOIN cs_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.del_flag = 0")
    List<String> selectRolesByUserId(Long userId);

    /** 查询用户拥有的权限标识（经角色-菜单关联汇总去重） */
    @Select("SELECT DISTINCT m.perms FROM cs_menu m " +
            "INNER JOIN cs_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN cs_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.del_flag = 0 AND m.perms IS NOT NULL")
    List<String> selectPermissionsByUserId(Long userId);

    /** 查询用户可见的菜单列表（按排序号升序，用于前端动态路由） */
    @Select("SELECT DISTINCT m.* FROM cs_menu m " +
            "INNER JOIN cs_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN cs_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.del_flag = 0 " +
            "ORDER BY m.sort_num ASC")
    List<com.ai.cs.base.entity.Menu> selectMenusByUserId(Long userId);
}
