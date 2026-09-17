package com.ai.cs.base.service;

import com.ai.cs.base.entity.Menu;
import com.ai.cs.base.mapper.MenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单权限管理服务
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Service
public class MenuService extends ServiceImpl<MenuMapper, Menu> {

    /**
     * 获取菜单树
     */
    public List<Menu> getMenuTree() {
        // 只取启用且未删除的菜单
        List<Menu> allMenus = this.list(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getStatus, 1)
                .eq(Menu::getDelFlag, 0)
                .orderByAsc(Menu::getSortNum));

        // 按父ID分组，便于 O(1) 查找子菜单
        Map<Long, List<Menu>> parentMap = allMenus.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() > 0)
                .collect(Collectors.groupingBy(Menu::getParentId));

        // parentId 为空或 0 视为根节点
        List<Menu> rootMenus = allMenus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0)
                .collect(Collectors.toList());

        return buildTree(rootMenus, parentMap);
    }

    /** 递归组装菜单树，并按 sortNum 排序（null 按 0 处理） */
    private List<Menu> buildTree(List<Menu> menus, Map<Long, List<Menu>> parentMap) {
        List<Menu> result = new ArrayList<>();
        for (Menu menu : menus) {
            List<Menu> children = parentMap.get(menu.getId());
            if (children != null) {
                menu.setChildren(buildTree(children, parentMap));
            }
            result.add(menu);
        }
        result.sort(Comparator.comparingInt(m -> m.getSortNum() != null ? m.getSortNum() : 0));
        return result;
    }
}
