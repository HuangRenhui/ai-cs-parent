package com.ai.cs.open.service;

import com.ai.cs.common.dto.BizEntity;
import com.ai.cs.common.dto.SceneQuickAction;
import com.ai.cs.common.dto.ToolInvokeDTO;
import com.ai.cs.common.dto.ToolInvokeResultDTO;
import com.ai.cs.common.dto.WidgetInitDTO;
import com.ai.cs.common.dto.WidgetInitVO;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.JwtUtil;
import com.ai.cs.common.util.SecretCipherUtil;
import com.ai.cs.common.util.ValidateUtil;
import com.ai.cs.open.entity.OpenConnector;
import com.ai.cs.open.entity.OpenPack;
import com.ai.cs.open.entity.OpenTool;
import com.ai.cs.open.entity.SceneConfig;
import com.ai.cs.open.entity.VisitorMap;
import com.ai.cs.open.mapper.OpenConnectorMapper;
import com.ai.cs.open.mapper.OpenPackMapper;
import com.ai.cs.open.mapper.OpenToolMapper;
import com.ai.cs.open.mapper.SceneConfigMapper;
import com.ai.cs.open.mapper.VisitorMapMapper;
import com.ai.cs.open.support.BaseSessionClient;
import com.ai.cs.open.util.ConnectorUrlGuard;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

/**
 * 开放平台核心服务：场景配置、行业包、连接器、开放工具的维护，
 * 以及 Widget 初始化与工具调用两大运行时链路。
 */
@Slf4j
@Service
public class OpenPlatformService extends ServiceImpl<OpenToolMapper, OpenTool> {

    @Resource
    private OpenConnectorMapper connectorMapper;
    @Resource
    private VisitorMapMapper visitorMapMapper;
    @Resource
    private OpenPackMapper packMapper;
    @Resource
    private SceneConfigMapper sceneConfigMapper;
    @Resource
    private ConnectorFieldMappingService fieldMappingService;
    @Resource
    private BaseSessionClient baseSessionClient;

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
            ConnectorUrlGuard.assertSafe(connector.getBaseUrl(), null);
        }
        // 鉴权 JSON 落库前加密，回显时仍是密文；invoke 时再解密
        if (StringUtils.hasText(connector.getAuthJson())) {
            connector.setAuthJson(SecretCipherUtil.encrypt(connector.getAuthJson()));
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
                // 如果从匿名变为登录，需要合并历史会话
                if (existing.getCustomerId() == null) {
                    mergeAnonymousSessions(existing.getVisitorRef(), dto.getCustomerId());
                }
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
        vo.setPackCode(StringUtils.hasText(dto.getPackCode()) ? dto.getPackCode().trim() : null);
        // 第三步：签发访客令牌（绑定租户，下游据此隔离会话与知识检索范围）；
        // 未识别客户用 0 占位，仅标识访客身份
        long visitorId = existing.getCustomerId() == null ? 0L : existing.getCustomerId();
        vo.setAccessToken(JwtUtil.generateVisitorToken(visitorId, "visitor:" + visitorRef, tenant));
        // 第四步：按场景下发开场白与快捷动作
        applySceneProfile(vo);
        return vo;
    }

    /**
     * 合并匿名会话到已登录客户（占位）
     *
     * <p>TODO 后续实现：按 visitorRef 调基础服务把历史会话的 customer_id 更新为正式客户 ID；
     * 不能按 customerId=0 全表更新，否则会把其他匿名访客的会话并过来。合并失败不阻断 Widget 初始化。</p>
     *
     * @param visitorRef 访客标识
     * @param customerId 正式客户 ID
     */
    private void mergeAnonymousSessions(String visitorRef, Long customerId) {
        log.info("[占位] 合并匿名会话 visitorRef={} customerId={}", visitorRef, customerId);
    }

    /**
     * 工具调用主链路（占位：不查工具、不校验、不调用连接器）
     *
     * <p>TODO 后续实现，依次经过五道关卡：
     * 1) <b>工具查找</b>：按工具名或绑定意图在注册表中找到工具，未命中返回 {@code source=registry} 失败结果；
     * 2) <b>行业包开关</b>：所属行业包关闭时返回 {@code source=pack-off} 失败结果；
     * 3) <b>连接器可用性</b>：绑定连接器不存在或已停用则抛业务异常；
     * 4) <b>风险确认</b>：write/critical 级工具必须带 {@code confirmed=true}，否则返回
     *    {@code source=confirm-required}；
     * 5) <b>幂等控制</b>：带幂等键的请求先以 {@code success=0} 占位行抢占执行权（靠幂等键唯一索引，
     *    插入撞索引即为并发占用，提示勿重复提交），已成功过的直接重放首次结果
     *    （{@code source=idempotent}）。
     * 全部通过后按连接器类型分发执行（REST 真实出站／MOCK 演示），并回写调用记录
     * （有幂等占位行则原地更新，否则追加审计）；异常同样落库留痕。</p>
     *
     * <p>当前不执行任何关卡与外部调用：直接返回 {@code implemented=false} 的失败结果，
     * 因此工具不会真正被调用，对方系统不会被触达。</p>
     *
     * @param dto 工具调用请求
     * @return 未实现的失败结果
     */
    @Transactional
    public ToolInvokeResultDTO invoke(ToolInvokeDTO dto) {
        log.warn("[占位] 开放工具调用未实现 toolName={} intentBind={}",
                dto == null ? null : dto.getToolName(), dto == null ? null : dto.getIntentBind());
        ToolInvokeResultDTO result = new ToolInvokeResultDTO();
        result.setSuccess(false);
        result.setToolName(dto == null ? null : dto.getToolName());
        result.setOutput("开放工具调用为占位实现，后端未接入工具查找、风险确认与连接器执行。");
        result.setSource("not-implemented");
        return result;
    }

    /**
     * REST 连接器出站（占位：不发起 HTTP 调用）
     *
     * <p>TODO 后续实现：拼接 {@code baseUrl + httpPath}（归一化斜杠）后发起调用。
     * 调用前必须再次经 {@code ConnectorUrlGuard.assertSafe} 做 SSRF 校验（防运行期地址被篡改）；
     * 使用禁止跟随重定向的请求工厂（防 302 跳内网绕过校验）；按工具 {@code timeoutMs} 设超时（缺省 5000ms）；
     * 注入鉴权头（见 {@link #applyConnectorAuth}）；请求体需按连接器字段映射生成（`ConnectorFieldMappingService.reverseMapping`）。</p>
     *
     * @return 占位返回 null
     */
    private String invokeRest(OpenConnector connector, OpenTool tool, ToolInvokeDTO dto) {
        log.info("[占位] REST 连接器出站未实现 tool={}", tool == null ? null : tool.getName());
        return null;
    }

    /**
     * 解密 authJson 并注入鉴权头（占位）
     *
     * <p>TODO 后续实现：解密连接器 authJson，按 type=bearer/header/basic 注入对应鉴权头；
     * 历史明文或非 JSON 配置应忽略，不影响 MOCK。</p>
     *
     * @param connector 连接器配置
     * @param headers   待注入的请求头
     */
    private void applyConnectorAuth(OpenConnector connector, HttpHeaders headers) {
        log.info("[占位] 注入连接器鉴权头 connectorId={}", connector == null ? null : connector.getId());
    }

}
