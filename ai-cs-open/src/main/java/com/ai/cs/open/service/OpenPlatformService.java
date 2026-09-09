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

/**
 * 开放平台核心服务：场景配置、行业包、连接器、开放工具的维护，
 * 以及 Widget 初始化与工具调用两大运行时链路。
 */
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

    /**
     * 查询全部场景配置，按排序号、ID 升序返回。
     */
    public List<SceneConfig> listScenes() {
        return sceneConfigMapper.selectList(new LambdaQueryWrapper<SceneConfig>()
                .orderByAsc(SceneConfig::getSortNum).orderByAsc(SceneConfig::getId));
    }

    /**
     * 新增或更新场景配置：场景编码统一转大写并做唯一性校验，缺省启用、排序号为 0。
     */
    public void saveScene(SceneConfig config) {
        if (config == null || !StringUtils.hasText(config.getScene())) {
            throw new BusinessException("场景编码不能为空");
        }
        if (!StringUtils.hasText(config.getSceneName())) {
            throw new BusinessException("场景名称不能为空");
        }
        // 场景编码统一大写，与 Widget 初始化时的解析逻辑保持一致
        config.setScene(config.getScene().trim().toUpperCase());
        // 唯一性校验：同编码场景只允许一条（更新时排除自身）
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

    /**
     * 删除场景配置。
     */
    public void deleteScene(Long id) {
        sceneConfigMapper.deleteById(id);
    }

    /**
     * 启用/停用场景配置，非法值一律按停用处理。
     */
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
            // 无业务实体时优先使用「空实体开场白」，避免占位符替换出空串的尴尬文案
            greeting = config.getGreetingEmpty();
        } else if (StringUtils.hasText(greeting)) {
            // 有实体时把开场白模板里的 {entityId} 替换为真实实体（如订单号）
            greeting = greeting.replace("{entityId}", entityId == null ? "" : entityId);
        }
        vo.setGreeting(greeting);
        if (StringUtils.hasText(config.getQuickActions())) {
            try {
                // 快捷动作存的是 JSON 数组，解析失败时降级为空列表，不阻断初始化
                List<SceneQuickAction> actions = JSON.parseArray(config.getQuickActions(), SceneQuickAction.class);
                vo.setQuickActions(actions == null ? List.of() : actions);
            } catch (Exception e) {
                vo.setQuickActions(List.of());
            }
        }
    }

    /**
     * 查找场景配置：优先精确匹配指定场景（仅启用状态），未命中时回退到 GENERAL 兜底场景。
     */
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

    /**
     * 取首个业务实体的 ID，用于开场白占位符替换；无实体时返回 null。
     */
    private String firstEntityId(List<BizEntity> entities) {
        if (entities == null || entities.isEmpty() || entities.get(0) == null) {
            return null;
        }
        return entities.get(0).getId();
    }

    /**
     * 查询全部行业包，按排序号、ID 升序返回。
     */
    public List<OpenPack> listPacks() {
        return packMapper.selectList(new LambdaQueryWrapper<OpenPack>().orderByAsc(OpenPack::getSortNum).orderByAsc(OpenPack::getId));
    }

    /**
     * 启用/停用指定行业包。
     */
    public void setPackEnabled(String code, Integer enabled) {
        OpenPack pack = requirePack(code);
        pack.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        packMapper.updateById(pack);
    }

    /**
     * 仅启用指定行业包，其余全部停用，实现行业间热切换。
     */
    public void activatePackExclusive(String code) {
        requirePack(code);
        List<OpenPack> packs = listPacks();
        for (OpenPack pack : packs) {
            pack.setEnabled(pack.getCode().equals(code) ? 1 : 0);
            packMapper.updateById(pack);
        }
    }

    /**
     * 按编码查询行业包，不存在时抛业务异常。
     */
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

    /**
     * 判断行业包是否启用；未归属任何包（编码为空）的工具视为始终可用。
     */
    private boolean packEnabled(String packCode) {
        if (!StringUtils.hasText(packCode)) {
            return true;
        }
        OpenPack pack = packMapper.selectOne(new LambdaQueryWrapper<OpenPack>().eq(OpenPack::getCode, packCode.trim()));
        return pack != null && Integer.valueOf(1).equals(pack.getEnabled());
    }

    /**
     * 查询全部连接器，按 ID 倒序返回。
     */
    public List<OpenConnector> listConnectors() {
        return connectorMapper.selectList(new LambdaQueryWrapper<OpenConnector>().orderByDesc(OpenConnector::getId));
    }

    /**
     * 新增或更新连接器：类型仅允许 MOCK/REST；REST 类型保存前先做 SSRF 地址安全校验。
     */
    public void saveConnector(OpenConnector connector) {
        if (connector == null || !StringUtils.hasText(connector.getName())) {
            throw new BusinessException("连接器名称不能为空");
        }
        String type = ValidateUtil.trimToNull(connector.getType());
        if (type == null || (!"MOCK".equalsIgnoreCase(type) && !"REST".equalsIgnoreCase(type))) {
            throw new BusinessException("连接器类型只能是 MOCK 或 REST");
        }
        connector.setType(type.toUpperCase());
        // REST 连接器会真实出站请求，保存时先校验地址，防止 SSRF 打内网
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

    /**
     * 删除连接器（逻辑删除）。
     */
    public void deleteConnector(Long id) {
        connectorMapper.deleteById(id);
    }

    /**
     * 查询全部开放工具，按 ID 倒序返回。
     */
    public List<OpenTool> listTools() {
        return this.list(new LambdaQueryWrapper<OpenTool>().orderByDesc(OpenTool::getId));
    }

    /**
     * 新增或更新开放工具：必须绑定已存在的连接器；风险等级缺省 read，超时下限 500ms。
     */
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

    /**
     * 删除开放工具（逻辑删除）。
     */
    public void deleteTool(Long id) {
        this.removeById(id);
    }

    /**
     * Widget 初始化主流程：
     * 1) 按「租户+渠道+访客标识」查询或建立访客映射（VisitorMap），保证同一访客多次进入身份一致；
     * 2) 组装初始化响应（场景、实体、客户 ID）；
     * 3) 签发访客 JWT 令牌；
     * 4) 按场景下发开场白与快捷动作（未配置时回退 GENERAL）。
     */
    public WidgetInitVO initWidget(WidgetInitDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getVisitorRef())) {
            throw new BusinessException("visitorRef 不能为空");
        }
        // 租户与渠道缺省值：default / web，保证老接入方不传也能用
        String tenant = StringUtils.hasText(dto.getTenantCode()) ? dto.getTenantCode().trim() : "default";
        String channel = StringUtils.hasText(dto.getChannel()) ? dto.getChannel().trim() : "web";
        String visitorRef = dto.getVisitorRef().trim();

        // 第一步：查询或建立访客映射，visitorRef 在「租户+渠道」内唯一
        LambdaQueryWrapper<VisitorMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VisitorMap::getTenantCode, tenant)
                .eq(VisitorMap::getVisitorRef, visitorRef)
                .eq(VisitorMap::getChannel, channel);
        VisitorMap existing = visitorMapMapper.selectOne(wrapper);
        if (existing == null) {
            // 首次进入：新建映射记录
            existing = new VisitorMap();
            existing.setTenantCode(tenant);
            existing.setChannel(channel);
            existing.setVisitorRef(visitorRef);
            existing.setCustomerId(dto.getCustomerId());
            existing.setScene(dto.getScene());
            existing.setEntitiesJson(JSON.toJSONString(dto.getEntities()));
            visitorMapMapper.insert(existing);
        } else {
            // 再次进入：仅在传入客户 ID 时更新映射（避免把已识别的客户冲掉），场景与实体每次都刷新
            if (dto.getCustomerId() != null) {
                existing.setCustomerId(dto.getCustomerId());
            }
            existing.setScene(dto.getScene());
            existing.setEntitiesJson(JSON.toJSONString(dto.getEntities()));
            visitorMapMapper.updateById(existing);
        }

        // 第二步：组装初始化响应
        WidgetInitVO vo = new WidgetInitVO();
        vo.setTenantCode(tenant);
        vo.setChannel(channel);
        vo.setVisitorRef(visitorRef);
        vo.setScene(dto.getScene());
        vo.setEntities(dto.getEntities() == null ? List.of() : dto.getEntities());
        vo.setCustomerId(existing.getCustomerId());
        // 第三步：签发访客令牌；未识别客户用 0 占位，仅标识访客身份
        long visitorId = existing.getCustomerId() == null ? 0L : existing.getCustomerId();
        vo.setAccessToken(JwtUtil.generateVisitorToken(visitorId, "visitor:" + visitorRef));
        // 第四步：按场景下发开场白与快捷动作
        applySceneProfile(vo);
        return vo;
    }

    /**
     * 工具调用主链路，依次经过五道关卡：
     * 1) 工具查找：按工具名或绑定意图在注册表中找到工具；
     * 2) 行业包开关：所属行业包关闭时拒绝调用；
     * 3) 连接器可用性：绑定连接器不存在或已停用则报错；
     * 4) 风险确认：write/critical 级工具必须携带人工确认标记；
     * 5) 幂等控制：带幂等键的重复请求直接重放首次结果，并发占用时提示勿重复提交。
     * 全部通过后才真正走 MOCK/REST 连接器执行，并把结果回写调用记录。
     */
    @Transactional
    public ToolInvokeResultDTO invoke(ToolInvokeDTO dto) {
        if (dto == null) {
            throw new BusinessException("调用参数不能为空");
        }
        // 关卡 1：工具查找
        OpenTool tool = findTool(dto);
        if (tool == null) {
            ToolInvokeResultDTO miss = new ToolInvokeResultDTO();
            miss.setSuccess(false);
            miss.setOutput("未注册对应工具，或所属行业包已关闭。请在「开放接入」启用行业包，不要在内核写死行业逻辑。");
            miss.setSource("registry");
            return miss;
        }
        // 关卡 2：行业包开关
        if (!packEnabled(tool.getPackCode())) {
            ToolInvokeResultDTO off = new ToolInvokeResultDTO();
            off.setSuccess(false);
            off.setToolName(tool.getName());
            off.setOutput("行业包「" + packName(tool.getPackCode()) + "」已关闭，不会调用 " + tool.getName() + "。可在开放接入中开启该包，或「仅启用」另一个行业。");
            off.setSource("pack-off");
            return off;
        }
        // 关卡 3：连接器可用性
        OpenConnector connector = connectorMapper.selectById(tool.getConnectorId());
        if (connector == null || connector.getEnabled() != null && connector.getEnabled() == 0) {
            throw new BusinessException("工具绑定的连接器不可用");
        }
        // 关卡 4：高风险工具（写/关键级）必须人工确认后才能执行
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
        // 关卡 5：幂等占位——success=1 表示已成功可直接重放；-1 表示并发冲突
        OpenToolInvoke pending = reserveIdempotent(dto);
        if (pending != null && Integer.valueOf(1).equals(pending.getSuccess())
                && StringUtils.hasText(pending.getResponseJson())) {
            // 幂等重放：相同幂等键已成功过，直接返回首次结果，避免重复执行写操作
            ToolInvokeResultDTO replay = new ToolInvokeResultDTO();
            replay.setSuccess(true);
            replay.setToolName(tool.getName());
            replay.setOutput(pending.getResponseJson());
            replay.setSource("idempotent");
            return replay;
        }
        if (pending != null && Integer.valueOf(-1).equals(pending.getSuccess())) {
            // 并发占用：另一个相同请求正在处理中
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
            // 按连接器类型分发：REST 真实出站，否则走 MOCK 演示数据
            if ("REST".equalsIgnoreCase(connector.getType())) {
                output = invokeRest(connector, tool, dto);
            } else {
                output = invokeMock(tool, dto);
            }
            // 回写调用结果（幂等占位行更新为成功，或追加一条审计记录）
            completeInvoke(pending, tool.getName(), dto, output, true);
            ToolInvokeResultDTO ok = new ToolInvokeResultDTO();
            ok.setSuccess(true);
            ok.setToolName(tool.getName());
            ok.setOutput(output);
            ok.setSource(source);
            return ok;
        } catch (Exception e) {
            // 调用异常同样落库留痕，便于排障与失败率统计
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
        // 未传幂等键：不做幂等控制，直接返回 null，后续走普通审计落库
        if (!StringUtils.hasText(dto.getIdempotencyKey())) {
            return null;
        }
        String key = dto.getIdempotencyKey().trim();
        OpenToolInvoke existing = invokeMapper.selectOne(new LambdaQueryWrapper<OpenToolInvoke>()
                .eq(OpenToolInvoke::getIdempotencyKey, key)
                .orderByAsc(OpenToolInvoke::getId)
                .last("LIMIT 1"));
        // 已有成功记录：返回给上层做结果重放
        if (existing != null && Integer.valueOf(1).equals(existing.getSuccess())) {
            return existing;
        }
        // 已有占位/失败记录：复用该行，本次调用结束时原地更新
        if (existing != null) {
            return existing;
        }
        // 无记录：先插入一行 success=0 的占位，靠幂等键唯一索引抢占执行权
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
            // 插入撞唯一索引：说明并发请求抢先占位，复查对方是否已成功
            OpenToolInvoke raced = invokeMapper.selectOne(new LambdaQueryWrapper<OpenToolInvoke>()
                    .eq(OpenToolInvoke::getIdempotencyKey, key)
                    .orderByAsc(OpenToolInvoke::getId)
                    .last("LIMIT 1"));
            if (raced != null && Integer.valueOf(1).equals(raced.getSuccess())) {
                return raced;
            }
            // 对方仍在处理：返回 success=-1 的内存态标记（不落库），提示调用方勿重复提交
            OpenToolInvoke occupied = new OpenToolInvoke();
            occupied.setSuccess(-1);
            return occupied;
        }
    }

    /**
     * 调用结束后回写结果：有幂等占位行则原地更新，否则追加一条审计记录。
     */
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

    /**
     * 查找目标工具：优先按工具名精确匹配，其次按绑定意图匹配；
     * 候选中优先返回所属行业包已启用的工具。
     */
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

    /**
     * 按编码取行业包名称，用于提示文案；查不到时原样返回编码。
     */
    private String packName(String code) {
        if (!StringUtils.hasText(code)) {
            return "未分组";
        }
        OpenPack pack = packMapper.selectOne(new LambdaQueryWrapper<OpenPack>().eq(OpenPack::getCode, code));
        return pack == null ? code : pack.getName();
    }

    /**
     * MOCK 连接器：按工具返回演示文案，用于未接真实系统时的联调演示。
     */
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

    /**
     * REST 连接器：拼接 baseUrl + httpPath 后真实发起 HTTP 调用。
     * 调用前再次做 SSRF 校验（防止运行期地址被篡改），并禁用重定向、
     * 按工具配置设置超时，避免被慢响应拖垮。
     */
    private String invokeRest(OpenConnector connector, OpenTool tool, ToolInvokeDTO dto) {
        if (!StringUtils.hasText(connector.getBaseUrl()) || !StringUtils.hasText(tool.getHttpPath())) {
            throw new BusinessException("REST 连接器需要 baseUrl 与工具 httpPath");
        }
        // 拼接完整地址并归一化斜杠
        String url = connector.getBaseUrl().replaceAll("/$", "") + "/" + tool.getHttpPath().replaceAll("^/", "");
        ConnectorUrlGuard.assertSafe(url);
        // 自定义连接工厂：禁止自动跟随重定向，防止借 302 跳转到内网地址绕过校验
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

    /**
     * 追加一条调用审计记录（无幂等键的调用走这里）。
     */
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
