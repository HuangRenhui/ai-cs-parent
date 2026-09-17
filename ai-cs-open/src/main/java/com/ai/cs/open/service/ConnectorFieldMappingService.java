package com.ai.cs.open.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.open.entity.ConnectorFieldMapping;
import com.ai.cs.open.entity.OpenConnector;
import com.ai.cs.open.mapper.ConnectorFieldMappingMapper;
import com.ai.cs.open.mapper.OpenConnectorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 连接器字段映射服务：管理对方系统字段与内核实体字段的映射关系。
 */
@Service
public class ConnectorFieldMappingService extends ServiceImpl<ConnectorFieldMappingMapper, ConnectorFieldMapping> {

    @Resource
    private ConnectorFieldMappingMapper fieldMappingMapper;
    @Resource
    private OpenConnectorMapper connectorMapper;

    /**
     * 查询指定连接器的全部字段映射。
     */
    public List<ConnectorFieldMapping> listMappingsByConnector(Long connectorId) {
        return fieldMappingMapper.selectList(new LambdaQueryWrapper<ConnectorFieldMapping>()
                .eq(ConnectorFieldMapping::getConnectorId, connectorId)
                .orderByAsc(ConnectorFieldMapping::getId));
    }

    /**
     * 新增或更新字段映射。
     */
    public void saveMapping(ConnectorFieldMapping mapping) {
        if (mapping == null) {
            throw new BusinessException("字段映射不能为空");
        }
        if (mapping.getConnectorId() == null) {
            throw new BusinessException("必须指定连接器");
        }
        if (connectorMapper.selectById(mapping.getConnectorId()) == null) {
            throw new BusinessException("连接器不存在");
        }
        if (!StringUtils.hasText(mapping.getSourceField())) {
            throw new BusinessException("对方系统字段名不能为空");
        }
        if (!StringUtils.hasText(mapping.getTargetField())) {
            throw new BusinessException("内核实体字段名不能为空");
        }
        if (!StringUtils.hasText(mapping.getFieldType())) {
            mapping.setFieldType("string");
        }
        if (mapping.getRequired() == null) {
            mapping.setRequired(0);
        }
        if (mapping.getId() == null) {
            fieldMappingMapper.insert(mapping);
        } else {
            fieldMappingMapper.updateById(mapping);
        }
    }

    /**
     * 删除字段映射（逻辑删除）。
     */
    public void deleteMapping(Long id) {
        fieldMappingMapper.deleteById(id);
    }

    /**
     * 批量保存字段映射。
     */
    @Transactional
    public void batchSaveMappings(Long connectorId, List<ConnectorFieldMapping> mappings) {
        if (connectorId == null) {
            throw new BusinessException("连接器 ID 不能为空");
        }
        // 先删除该连接器的旧映射
        fieldMappingMapper.delete(new LambdaQueryWrapper<ConnectorFieldMapping>()
                .eq(ConnectorFieldMapping::getConnectorId, connectorId));
        // 批量插入新映射
        for (ConnectorFieldMapping mapping : mappings) {
            mapping.setConnectorId(connectorId);
            mapping.setId(null);
            saveMapping(mapping);
        }
    }

    /**
     * 应用字段映射，将对方系统的数据转换为内核格式。
     */
    public Map<String, Object> applyMapping(Long connectorId, Map<String, Object> sourceData) {
        List<ConnectorFieldMapping> mappings = listMappingsByConnector(connectorId);
        Map<String, Object> targetData = new java.util.HashMap<>();
        
        for (ConnectorFieldMapping mapping : mappings) {
            Object value = sourceData.get(mapping.getSourceField());
            if (value != null) {
                targetData.put(mapping.getTargetField(), applyTransform(value, mapping.getTransformRule()));
            } else if (mapping.getRequired() != null && mapping.getRequired() == 1 && StringUtils.hasText(mapping.getDefaultValue())) {
                targetData.put(mapping.getTargetField(), mapping.getDefaultValue());
            }
        }
        
        return targetData;
    }

    /**
     * 反向应用字段映射，将内核数据转换为对方系统格式。
     */
    public Map<String, Object> reverseMapping(Long connectorId, Map<String, Object> targetData) {
        List<ConnectorFieldMapping> mappings = listMappingsByConnector(connectorId);
        Map<String, Object> sourceData = new java.util.HashMap<>();
        
        for (ConnectorFieldMapping mapping : mappings) {
            Object value = targetData.get(mapping.getTargetField());
            if (value != null) {
                // TODO: 根据 transformRule 进行反向转换
                sourceData.put(mapping.getSourceField(), value);
            }
        }
        
        return sourceData;
    }

    /**
     * 按 transformRule JSON 做简单转换：prefix / suffix / enumMap。
     */
    private Object applyTransform(Object value, String transformRule) {
        if (value == null || !StringUtils.hasText(transformRule)) {
            return value;
        }
        try {
            com.alibaba.fastjson2.JSONObject rule = com.alibaba.fastjson2.JSON.parseObject(transformRule);
            if (rule == null) {
                return value;
            }
            String text = String.valueOf(value);
            if (rule.containsKey("enumMap") && rule.getJSONObject("enumMap") != null) {
                String mapped = rule.getJSONObject("enumMap").getString(text);
                if (mapped != null) {
                    text = mapped;
                }
            }
            if (rule.containsKey("prefix")) {
                text = rule.getString("prefix") + text;
            }
            if (rule.containsKey("suffix")) {
                text = text + rule.getString("suffix");
            }
            return text;
        } catch (Exception e) {
            return value;
        }
    }
}
