package com.ai.cs.open.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.open.entity.OpenApiImport;
import com.ai.cs.open.entity.OpenTool;
import com.ai.cs.open.mapper.OpenApiImportMapper;
import com.ai.cs.open.util.ConnectorUrlGuard;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * OpenAPI 导入服务：从 Swagger/OpenAPI 规范导入工具列表。
 */
@Slf4j
@Service
public class OpenApiImportService extends ServiceImpl<OpenApiImportMapper, OpenApiImport> {

    @Resource
    private OpenApiImportMapper openApiImportMapper;
    @Resource
    private OpenPlatformService openPlatformService;

    /**
     * 查询全部导入记录，按 ID 倒序返回。
     */
    public List<OpenApiImport> listImports() {
        return openApiImportMapper.selectList(new LambdaQueryWrapper<OpenApiImport>()
                .orderByDesc(OpenApiImport::getId));
    }

    /**
     * 按租户查询导入记录。
     */
    public List<OpenApiImport> listImportsByTenant(String tenantCode) {
        return openApiImportMapper.selectList(new LambdaQueryWrapper<OpenApiImport>()
                .eq(OpenApiImport::getTenantCode, tenantCode)
                .orderByDesc(OpenApiImport::getId));
    }

    /**
     * 创建导入任务。
     */
    @Transactional
    public OpenApiImport createImport(OpenApiImport importRecord) {
        if (importRecord == null || !StringUtils.hasText(importRecord.getImportName())) {
            throw new BusinessException("导入名称不能为空");
        }
        if (!StringUtils.hasText(importRecord.getSourceUrl())) {
            throw new BusinessException("OpenAPI 文档 URL 不能为空");
        }
        if (importRecord.getConnectorId() == null) {
            throw new BusinessException("必须绑定连接器");
        }
        importRecord.setStatus("pending");
        importRecord.setToolCount(0);
        openApiImportMapper.insert(importRecord);
        return importRecord;
    }

    /**
     * 执行导入：拉取 OpenAPI JSON，按 paths 生成 OpenTool（占位）
     *
     * <p>TODO 后续实现：校验源 URL 安全后拉取 OpenAPI/Swagger JSON，遍历 paths 下各 HTTP 方法
     * 注册为 OpenTool，并回写导入状态（processing/success/failed）、工具数与错误信息。</p>
     *
     * @param importId 导入记录 ID
     */
    @Transactional
    public void executeImport(Long importId) {
        log.info("[占位] 执行 OpenAPI 导入 importId={}", importId);
    }

    /**
     * 删除导入记录（逻辑删除）。
     */
    public void deleteImport(Long id) {
        openApiImportMapper.deleteById(id);
    }
}
