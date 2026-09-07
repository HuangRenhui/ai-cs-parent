package com.ai.cs.open.service;

import com.ai.cs.common.dto.BizEntity;
import com.ai.cs.common.dto.SceneQuickAction;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.dto.WidgetInitDTO;
import com.ai.cs.common.dto.WidgetInitVO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.JwtUtil;
import com.ai.cs.common.util.ValidateUtil;
import com.ai.cs.open.entity.OpenConnector;
import com.ai.cs.open.entity.OpenPack;
import com.ai.cs.open.entity.OpenTool;
import com.ai.cs.open.entity.OpenToolInvoke;
import com.ai.cs.open.entity.SceneConfig;
import com.ai.cs.open.entity.VisitorMap;
import com.ai.cs.open.mapper.OpenConnectorMapper;
import com.ai.cs.open.mapper.OpenPackMapper;
import com.ai.cs.open.mapper.OpenToolInvokeMapper;
import com.ai.cs.open.mapper.OpenToolMapper;
import com.ai.cs.open.mapper.SceneConfigMapper;
import com.ai.cs.open.mapper.VisitorMapMapper;
import com.ai.cs.open.util.ConnectorUrlGuard;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OpenPlatformService extends ServiceImpl<OpenToolMapper, OpenTool> {

    @Resource
    private OpenConnectorMapper connectorMapper;
    @Resource
    private OpenToolInvokeMapper invokeMapper;
    @Resource
    private VisitorMapMapper visitorMapMapper;
    @Resource
    private OpenPackMapper packMapper;
    @Resource
    private SceneConfigMapper sceneConfigMapper;

    // ==================== 场景配置 ====================

    public List<SceneConfig> listScenes() {
        return sceneConfigMapper.selectList(new LambdaQueryWrapper<SceneConfig>()
                .orderByAsc(SceneConfig::getSortNum).orderByAsc(SceneConfig::getId));
    }

    public void saveScene(SceneConfig config) {
        if (config == null || !StringUtils.hasText(config.getScene())) {
            throw new BusinessException("场景编码不能为空");
        }
        if (!StringUtils.hasText(config.getSceneName())) {
            throw new BusinessException("场景名称不能为空");
        }
        config.setScene(config.getScene().trim().toUpperCase());
        SceneConfig dup = sceneConfigMapper.selectOne(new LambdaQueryWrapper<SceneConfig>()
                .eq(SceneConfig::getScene, config.getScene())
                .ne(config.getId() != null, SceneConfig::getId, config.getId())
                .last("limit 1"));
        if (dup != null) {
            throw new BusinessException("场景编码[" + config.getScene() + "]已存在");
        }
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        if (config.getSortNum() == null) {
            config.setSortNum(0);
        }
        if (config.getId() == null) {
            sceneConfigMapper.insert(config);
        } else {
            sceneConfigMapper.updateById(config);
        }
    }

    public void deleteScene(Long id) {
        sceneConfigMapper.deleteById(id);
    }

    public void setSceneEnabled(Long id, Integer enabled) {
        SceneConfig config = sceneConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("场景配置不存在");
        }
        config.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        sceneConfigMapper.updateById(config);
    }

    /**
     * 按场景解析开场白与快捷动作，写入 init 响应。
     * scene 未配置时回退 GENERAL；{entityId} 用首个实体替换，无实体时用 greeting_empty。
     */
    private void applySceneProfile(WidgetInitVO vo) {
        SceneConfig config = findSceneConfig(vo.getScene());
        if (config == null) {
            return;
        }
        String entityId = firstEntityId(vo.getEntities());
        String greeting = config.getGreeting();
        if (!StringUtils.hasText(entityId) && StringUtils.hasText(config.getGreetingEmpty())) {
            greeting = config.getGreetingEmpty();
        } else if (StringUtils.hasText(greeting)) {
            greeting = greeting.replace("{entityId}", entityId == null ? "" : entityId);
        }
        vo.setGreeting(greeting);
        if (StringUtils.hasText(config.getQuickActions())) {
            try {
                List<SceneQuickAction> actions = JSON.parseArray(config.getQuickActions(), SceneQuickAction.class);
                vo.setQuickActions(actions == null ? List.of() : actions);
            } catch (Exception e) {
                vo.setQuickActions(List.of());
            }
        }
    }

    private SceneConfig findSceneConfig(String scene) {
        if (StringUtils.hasText(scene)) {
            SceneConfig exact = sceneConfigMapper.selectOne(new LambdaQueryWrapper<SceneConfig>()
                    .eq(SceneConfig::getEnabled, 1)
                    .eq(SceneConfig::getScene, scene.trim().toUpperCase())
                    .last("limit 1"));
            if (exact != null) {
                return exact;
            }
        }
        return sceneConfigMapper.selectOne(new LambdaQueryWrapper<SceneConfig>()
                .eq(SceneConfig::getEnabled, 1)
                .eq(SceneConfig::getScene, "GENERAL")
                .last("limit 1"));
    }

    private String firstEntityId(List<BizEntity> entities) {
        if (entities == null || entities.isEmpty() || entities.get(0) == null) {
            return null;
        }
        return entities.get(0).getId();
    }

    public List<OpenPack> listPacks() {
        return packMapper.selectList(new LambdaQueryWrapper<OpenPack>().orderByAsc(OpenPack::getSortNum).orderByAsc(OpenPack::getId));
    }

    public void setPackEnabled(String code, Integer enabled) {
        OpenPack pack = requirePack(code);
        pack.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        packMapper.updateById(pack);
    }

    public void activatePackExclusive(String code) {
        requirePack(code);
        List<OpenPack> packs = listPacks();
        for (OpenPack pack : packs) {
            pack.setEnabled(pack.getCode().equals(code) ? 1 : 0);
            packMapper.updateById(pack);
        }
    }

    private OpenPack requirePack(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("行业包编码不能为空");
        }
        OpenPack pack = packMapper.selectOne(new LambdaQueryWrapper<OpenPack>().eq(OpenPack::getCode, code.trim()));
        if (pack == null) {
            throw new BusinessException("行业包不存在：" + code);
        }
        return pack;
    }

    private boolean packEnabled(String packCode) {
        if (!StringUtils.hasText(packCode)) {
            return true;
        }
        OpenPack pack = packMapper.selectOne(new LambdaQueryWrapper<OpenPack>().eq(OpenPack::getCode, packCode.trim()));
        return pack != null && Integer.valueOf(1).equals(pack.getEnabled());
    }

    public List<OpenConnector> listConnectors() {
        return connectorMapper.selectList(new LambdaQueryWrapper<OpenConnector>().orderByDesc(OpenConnector::getId));
    }

    public void saveConnector(OpenConnector connector) {
        if (connector == null || !StringUtils.hasText(connector.getName())) {
            throw new BusinessException("连接器名称不能为空");
        }
        String type = ValidateUtil.trimToNull(connector.getType());
        if (type == null || (!"MOCK".equalsIgnoreCase(type) && !"REST".equalsIgnoreCase(type))) {
            throw new BusinessException("连接器类型只能是 MOCK 或 REST");
        }
        connector.setType(type.toUpperCase());
        if ("REST".equalsIgnoreCase(connector.getType()) && StringUtils.hasText(connector.getBaseUrl())) {
            ConnectorUrlGuard.assertSafe(connector.getBaseUrl());
        }
        if (connector.getEnabled() == null) {
            connector.setEnabled(1);
        }
        if (connector.getId() == null) {
            connectorMapper.insert(connector);
        } else {
            connectorMapper.updateById(connector);
        }
    }

    public void deleteConnector(Long id) {
        connectorMapper.deleteById(id);
    }

    public List<OpenTool> listTools() {
        return this.list(new LambdaQueryWrapper<OpenTool>().orderByDesc(OpenTool::getId));
    }

    public void saveTool(OpenTool tool) {
        if (tool == null || !StringUtils.hasText(tool.getName())) {
            throw new BusinessException("工具名称不能为空");
        }
        tool.setName(tool.getName().trim());
        if (tool.getConnectorId() == null) {
            throw new BusinessException("必须绑定连接器");
        }
        if (connectorMapper.selectById(tool.getConnectorId()) == null) {
            throw new BusinessException("连接器不存在");
        }
        if (!StringUtils.hasText(tool.getRisk())) {
            tool.setRisk("read");
        }
        if (tool.getTimeoutMs() == null || tool.getTimeoutMs() < 500) {
            tool.setTimeoutMs(5000);
        }
        if (tool.getId() == null) {
            this.save(tool);
        } else {
            this.updateById(tool);
        }
    }

    public void deleteTool(Long id) {
        this.removeById(id);
    }

    public WidgetInitVO initWidget(WidgetInitDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getVisitorRef())) {
            throw new BusinessException("visitorRef 不能为空");
        }
        String tenant = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode().trim() : "default";
        String channel = StringUtils.hasText(dto.getChannel()) ? dto.getChannel().trim() : "web";
        String visitorRef = dto.getVisitorRef().trim();

        LambdaQueryWrapper<VisitorMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VisitorMap::getTenantCode, tenant)
                .eq(VisitorMap::getVisitorRef, visitorRef)
                .eq(VisitorMap::getChannel, channel);
        VisitorMap existing = visitorMapMapper.selectOne(wrapper);
        if (existing == null) {
            existing = new VisitorMap();
            existing.setTenantCode(tenant);
            existing.setChannel(channel);
            existing.setVisitorRef(visitorRef);
            existing.setCustomerId(dto.getCustomerId());
            existing.setScene(dto.getScene());
            existing.setEntitiesJson(JSON.toJSONString(dto.getEntities()));
            visitorMapMapper.insert(existing);
        } else {
            if (dto.getCustomerId() != null) {
                existing.setCustomerId(dto.getCustomerId());
            }
            existing.setScene(dto.getScene());
            existing.setEntitiesJson(JSON.toJSONString(dto.getEntities()));
            visitorMapMapper.updateById(existing);
        }

        WidgetInitVO vo = new WidgetInitVO();
        vo.setTenantCode(tenant);
        vo.setChannel(channel);
        vo.setVisitorRef(visitorRef);
        vo.setScene(dto.getScene());
        vo.setEntities(dto.getEntities() == null ? List.of() : dto.getEntities());
        vo.setCustomerId(existing.getCustomerId());
        long visitorId = existing.getCustomerId() == null ? 0L : existing.getCustomerId();
        vo.setAccessToken(JwtUtil.generateVisitorToken(visitorId, "visitor:" + visitorRef));
        applySceneProfile(vo);
        return vo;
    }

    @Transactional
    public ToolInvokeResultDTO invoke(ToolInvokeDTO dto) {
        if (dto == null) {
            throw new BusinessException("调用参数不能为空");
        }
        OpenTool tool = findTool(dto);
        if (tool == null) {
            ToolInvokeResultDTO miss = new ToolInvokeResultDTO();
            miss.setSuccess(false);
            miss.setOutput("未注册对应工具，或所属行业包已关闭。请在「开放接入」启用行业包，不要在内核写死行业逻辑。");
            miss.setSource("registry");
            return miss;
        }
        if (!packEnabled(tool.getPackCode())) {
            ToolInvokeResultDTO off = new ToolInvokeResultDTO();
            off.setSuccess(false);
            off.setToolName(tool.getName());
            off.setOutput("行业包「" + packName(tool.getPackCode()) + "」已关闭，不会调用 " + tool.getName() + "。可在开放接入中开启该包，或「仅启用」另一个行业。");
            off.setSource("pack-off");
            return off;
        }
        OpenConnector connector = connectorMapper.selectById(tool.getConnectorId());
        if (connector == null || connector.getEnabled() != null && connector.getEnabled() == 0) {
            throw new BusinessException("工具绑定的连接器不可用");
        }
        if ("critical".equalsIgnoreCase(tool.getRisk()) || "write".equalsIgnoreCase(tool.getRisk())) {
            if (!Boolean.TRUE.equals(dto.getConfirmed())) {
                ToolInvokeResultDTO need = new ToolInvokeResultDTO();
                need.setSuccess(false);
                need.setToolName(tool.getName());
                need.setOutput("高风险操作需人工确认。请回复「确认」后再执行 " + tool.getName() + "。");
                need.setSource("confirm-required");
                return need;
            }
        }
        OpenToolInvoke pending = reserveIdempotent(dto);
        if (pending != null && Integer.valueOf(1).equals(pending.getSuccess())
                && StringUtils.hasText(pending.getResponseJson())) {
            ToolInvokeResultDTO replay = new ToolInvokeResultDTO();
            replay.setSuccess(true);
            replay.setToolName(tool.getName());
            replay.setOutput(pending.getResponseJson());
            replay.setSource("idempotent");
            return replay;
        }
        if (pending != null && Integer.valueOf(-1).equals(pending.getSuccess())) {
            ToolInvokeResultDTO busy = new ToolInvokeResultDTO();
            busy.setSuccess(false);
            busy.setToolName(tool.getName());
            busy.setOutput("相同请求正在处理，请勿重复提交。");
            busy.setSource("idempotent");
            return busy;
        }

        String output;
        String source = connector.getType();
        try {
            if ("REST".equalsIgnoreCase(connector.getType())) {
                output = invokeRest(connector, tool, dto);
            } else {
                output = invokeMock(tool, dto);
            }
            completeInvoke(pending, tool.getName(), dto, output, true);
            ToolInvokeResultDTO ok = new ToolInvokeResultDTO();
            ok.setSuccess(true);
            ok.setToolName(tool.getName());
            ok.setOutput(output);
            ok.setSource(source);
            return ok;
        } catch (Exception e) {
            completeInvoke(pending, tool.getName(), dto, e.getMessage(), false);
            ToolInvokeResultDTO fail = new ToolInvokeResultDTO();
            fail.setSuccess(false);
            fail.setToolName(tool.getName());
            fail.setOutput("连接器调用失败：" + e.getMessage());
            fail.setSource(source);
            return fail;
        }
    }

    /**
     * @return 已成功的行（重放）、success=-1 表示并发占用、success=0 的行供本次更新；无幂等键时返回 null
     */
    private OpenToolInvoke reserveIdempotent(ToolInvokeDTO dto) {
        if (!StringUtils.hasText(dto.getIdempotencyKey())) {
            return null;
        }
        String key = dto.getIdempotencyKey().trim();
        OpenToolInvoke existing = invokeMapper.selectOne(new LambdaQueryWrapper<OpenToolInvoke>()
                .eq(OpenToolInvoke::getIdempotencyKey, key)
                .orderByAsc(OpenToolInvoke::getId)
                .last("LIMIT 1"));
        if (existing != null && Integer.valueOf(1).equals(existing.getSuccess())) {
            return existing;
        }
        if (existing != null) {
            return existing;
        }
        OpenToolInvoke pending = new OpenToolInvoke();
        pending.setToolName("");
        pending.setSessionId(dto.getSessionId());
        pending.setRequestJson(JSON.toJSONString(dto));
        pending.setIdempotencyKey(key);
        pending.setSuccess(0);
        pending.setCreateTime(LocalDateTime.now());
        try {
            invokeMapper.insert(pending);
            return pending;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            OpenToolInvoke raced = invokeMapper.selectOne(new LambdaQueryWrapper<OpenToolInvoke>()
                    .eq(OpenToolInvoke::getIdempotencyKey, key)
                    .orderByAsc(OpenToolInvoke::getId)
                    .last("LIMIT 1"));
            if (raced != null && Integer.valueOf(1).equals(raced.getSuccess())) {
                return raced;
            }
            OpenToolInvoke occupied = new OpenToolInvoke();
            occupied.setSuccess(-1);
            return occupied;
        }
    }

    private void completeInvoke(OpenToolInvoke pending, String toolName, ToolInvokeDTO dto, String output, boolean success) {
        if (pending != null && pending.getId() != null) {
            pending.setToolName(toolName);
            pending.setRequestJson(JSON.toJSONString(dto));
            pending.setResponseJson(output);
            pending.setSuccess(success ? 1 : 0);
            invokeMapper.updateById(pending);
            return;
        }
        logInvoke(toolName, dto, output, success);
    }

    private OpenTool findTool(ToolInvokeDTO dto) {
        List<OpenTool> candidates = List.of();
        if (StringUtils.hasText(dto.getToolName())) {
            candidates = this.list(new LambdaQueryWrapper<OpenTool>().eq(OpenTool::getName, dto.getToolName().trim()));
        }
        if (candidates.isEmpty() && StringUtils.hasText(dto.getIntentBind())) {
            candidates = this.list(new LambdaQueryWrapper<OpenTool>().eq(OpenTool::getIntentBind, dto.getIntentBind().trim()));
        }
        for (OpenTool tool : candidates) {
            if (packEnabled(tool.getPackCode())) {
                return tool;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private String packName(String code) {
        if (!StringUtils.hasText(code)) {
            return "未分组";
        }
        OpenPack pack = packMapper.selectOne(new LambdaQueryWrapper<OpenPack>().eq(OpenPack::getCode, code));
        return pack == null ? code : pack.getName();
    }

    private String invokeMock(OpenTool tool, ToolInvokeDTO dto) {
        String entityType = StringUtils.hasText(dto.getEntityType()) ? dto.getEntityType() : "entity";
        String entityId = StringUtils.hasText(dto.getEntityId()) ? dto.getEntityId() : "(未提供)";
        if ("apply_refund".equals(tool.getName()) || "退款".equals(tool.getIntentBind())) {
            return "【电商包 MOCK】已向对方 OMS 提交 " + tool.getName() + "，实体 " + entityType + "/" + entityId + "。";
        }
        if ("query_account".equals(tool.getName())) {
            return "【金融包 MOCK】账户 " + entityId + " 状态正常，近 3 笔交易已脱敏展示。冻卡等动作需人工复核，不会自动执行。";
        }
        return "【演示连接器】查询 " + entityType + " " + entityId + "（工具 " + tool.getName()
                + "）：状态=处理中。这是 MOCK 结果，请将连接器切换为对方系统 REST。";
    }

    private String invokeRest(OpenConnector connector, OpenTool tool, ToolInvokeDTO dto) {
        if (!StringUtils.hasText(connector.getBaseUrl()) || !StringUtils.hasText(tool.getHttpPath())) {
            throw new BusinessException("REST 连接器需要 baseUrl 与工具 httpPath");
        }
        String url = connector.getBaseUrl().replaceAll("/$", "") + "/" + tool.getHttpPath().replaceAll("^/", "");
        ConnectorUrlGuard.assertSafe(url);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        int timeout = tool.getTimeoutMs() == null ? 5000 : tool.getTimeoutMs();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestTemplate rest = new RestTemplate(factory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpMethod method = HttpMethod.valueOf(StringUtils.hasText(tool.getHttpMethod()) ? tool.getHttpMethod().toUpperCase() : "POST");
        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(dto), headers);
        ResponseEntity<String> response = rest.exchange(url, method, entity, String.class);
        return response.getBody() == null ? "" : response.getBody();
    }

    private void logInvoke(String toolName, ToolInvokeDTO dto, String output, boolean success) {
        OpenToolInvoke row = new OpenToolInvoke();
        row.setToolName(toolName);
        row.setSessionId(dto.getSessionId());
        row.setRequestJson(JSON.toJSONString(dto));
        row.setResponseJson(output);
        row.setIdempotencyKey(StringUtils.hasText(dto.getIdempotencyKey()) ? dto.getIdempotencyKey().trim() : null);
        row.setSuccess(success ? 1 : 0);
        row.setCreateTime(LocalDateTime.now());
        invokeMapper.insert(row);
    }
}
