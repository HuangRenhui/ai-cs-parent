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
        List<Menu> allMenus = this.list(new LambdaQueryWrapper<Menu>()
                .eq(Menu::getStatus, 1)
                .eq(Menu::getDelFlag, 0)
                .orderByAsc(Menu::getSortNum));

        Map<Long, List<Menu>> parentMap = allMenus.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() > 0)
                .collect(Collectors.groupingBy(Menu::getParentId));

        List<Menu> rootMenus = allMenus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0)
                .collect(Collectors.toList());

        return buildTree(rootMenus, parentMap);
    }

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
