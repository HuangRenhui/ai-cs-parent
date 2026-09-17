package com.ai.cs.open.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.open.entity.CardTemplate;
import com.ai.cs.open.mapper.CardTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 卡片模板服务：管理通用卡片协议（实体卡/按钮/表单）。
 */
@Service
public class CardTemplateService extends ServiceImpl<CardTemplateMapper, CardTemplate> {

    @Resource
    private CardTemplateMapper cardTemplateMapper;

    /**
     * 查询全部卡片模板，按 ID 倒序返回。
     */
    public List<CardTemplate> listCardTemplates() {
        return cardTemplateMapper.selectList(new LambdaQueryWrapper<CardTemplate>()
                .orderByDesc(CardTemplate::getId));
    }

    /**
     * 按租户查询卡片模板。
     */
    public List<CardTemplate> listCardTemplatesByTenant(String tenantCode) {
        return cardTemplateMapper.selectList(new LambdaQueryWrapper<CardTemplate>()
                .eq(CardTemplate::getTenantCode, tenantCode)
                .orderByDesc(CardTemplate::getId));
    }

    /**
     * 按行业包查询卡片模板。
     */
    public List<CardTemplate> listCardTemplatesByPack(String packCode) {
        return cardTemplateMapper.selectList(new LambdaQueryWrapper<CardTemplate>()
                .eq(CardTemplate::getPackCode, packCode)
                .eq(CardTemplate::getEnabled, 1)
                .orderByDesc(CardTemplate::getId));
    }

    /**
     * 按模板编码查询启用的卡片模板。
     */
    public CardTemplate findCardTemplateByCode(String templateCode, String tenantCode) {
        return cardTemplateMapper.selectOne(new LambdaQueryWrapper<CardTemplate>()
                .eq(CardTemplate::getTemplateCode, templateCode)
                .eq(CardTemplate::getTenantCode, tenantCode)
                .eq(CardTemplate::getEnabled, 1)
                .last("limit 1"));
    }

    /**
     * 新增或更新卡片模板。
     */
    public void saveCardTemplate(CardTemplate template) {
        if (template == null || !StringUtils.hasText(template.getTemplateCode())) {
            throw new BusinessException("模板编码不能为空");
        }
        if (!StringUtils.hasText(template.getTemplateName())) {
            throw new BusinessException("模板名称不能为空");
        }
        if (!StringUtils.hasText(template.getCardType())) {
            throw new BusinessException("卡片类型不能为空");
        }
        if (!StringUtils.hasText(template.getContentJson())) {
            throw new BusinessException("卡片内容不能为空");
        }
        // 模板编码统一大写
        template.setTemplateCode(template.getTemplateCode().trim().toUpperCase());
        // 唯一性校验
        CardTemplate dup = cardTemplateMapper.selectOne(new LambdaQueryWrapper<CardTemplate>()
                .eq(CardTemplate::getTemplateCode, template.getTemplateCode())
                .eq(CardTemplate::getTenantCode, template.getTenantCode())
                .ne(template.getId() != null, CardTemplate::getId, template.getId())
                .last("limit 1"));
        if (dup != null) {
            throw new BusinessException("模板编码[" + template.getTemplateCode() + "]已存在");
        }
        if (template.getEnabled() == null) {
            template.setEnabled(1);
        }
        if (template.getId() == null) {
            cardTemplateMapper.insert(template);
        } else {
            cardTemplateMapper.updateById(template);
        }
    }

    /**
     * 删除卡片模板（逻辑删除）。
     */
    public void deleteCardTemplate(Long id) {
        cardTemplateMapper.deleteById(id);
    }

    /**
     * 启用/停用卡片模板。
     */
    public void setCardTemplateEnabled(Long id, Integer enabled) {
        CardTemplate template = cardTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("卡片模板不存在");
        }
        template.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        cardTemplateMapper.updateById(template);
    }

    /**
     * 渲染卡片模板，将占位符替换为实际数据。
     */
    public String renderCardTemplate(String templateCode, String tenantCode, java.util.Map<String, Object> data) {
        CardTemplate template = findCardTemplateByCode(templateCode, tenantCode);
        if (template == null) {
            throw new BusinessException("卡片模板不存在：" + templateCode);
        }
        String content = template.getContentJson();
        if (data == null || data.isEmpty() || !StringUtils.hasText(content)) {
            return content;
        }
        String rendered = content;
        for (java.util.Map.Entry<String, Object> e : data.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue());
            rendered = rendered.replace("{{" + e.getKey() + "}}", value);
            rendered = rendered.replace("${" + e.getKey() + "}", value);
        }
        return rendered;
    }
}
