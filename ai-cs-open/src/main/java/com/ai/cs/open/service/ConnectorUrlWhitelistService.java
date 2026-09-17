package com.ai.cs.open.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.open.entity.ConnectorUrlWhitelist;
import com.ai.cs.open.mapper.ConnectorUrlWhitelistMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 连接器出站 URL 白名单管理。
 */
@Service
public class ConnectorUrlWhitelistService extends ServiceImpl<ConnectorUrlWhitelistMapper, ConnectorUrlWhitelist> {

    /**
     * 按租户列出白名单，未传租户则返回全部。
     */
    public List<ConnectorUrlWhitelist> listByTenant(String tenantCode) {
        LambdaQueryWrapper<ConnectorUrlWhitelist> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantCode)) {
            wrapper.eq(ConnectorUrlWhitelist::getTenantCode, tenantCode);
        }
        return this.list(wrapper.orderByDesc(ConnectorUrlWhitelist::getId));
    }

    /**
     * 新增或更新白名单条目。
     */
    public void saveEntry(ConnectorUrlWhitelist entry) {
        if (entry == null || !StringUtils.hasText(entry.getTenantCode())) {
            throw new BusinessException("租户编码不能为空");
        }
        if (!StringUtils.hasText(entry.getDomainPattern())) {
            throw new BusinessException("域名模式不能为空");
        }
        entry.setDomainPattern(entry.getDomainPattern().trim().toLowerCase());
        if (entry.getId() == null) {
            this.save(entry);
        } else {
            this.updateById(entry);
        }
    }
}
