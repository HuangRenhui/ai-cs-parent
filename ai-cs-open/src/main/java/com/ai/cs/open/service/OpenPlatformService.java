package com.ai.cs.open.service;

import com.ai.cs.common.dto.*;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.JwtUtil;
import com.ai.cs.common.util.SecretCipherUtil;
import com.ai.cs.common.util.ValidateUtil;
import com.ai.cs.open.entity.*;
import com.ai.cs.open.mapper.*;
import com.ai.cs.open.support.BaseSessionClient;
import com.ai.cs.open.util.ConnectorUrlGuard;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    /**
     * 调用记录落库组件：独立 Bean 才能让 {@code REQUIRES_NEW} 生效
     * （同类内部调用不走 Spring 代理，事务注解会失效）
     */
    @Resource
    private ConnectorInvokeRecorder recorder;
    /**
     * query 型鉴权在内部传递时的约定 header key（不会真的发出去）
     */
    private static final String AUTH_QUERY_HEADER = "X-Internal-Auth-Query";
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
     * 工具调用主链路：五道关卡依次通过后分发执行，全程留痕。
     *
     * <p><b>关卡顺序不可调换</b>，每一道都是在「越靠前拦截、代价越小」的位置：</p>
     * <ol>
     *   <li><b>工具查找</b>：找不到工具说明调用方传错或工具被删，直接失败，不该产生任何外部副作用</li>
     *   <li><b>行业包开关</b>：包关闭时该行业所有工具整体不可用——在鉴权重校验之前拦住，
     *       是为了让「关闭行业包」这个运营动作真正生效，而不是让请求打到对方系统</li>
     *   <li><b>连接器可用性</b>：连接器不存在或停用则无法出站，在风险确认前拦下，
     *       避免用户确认了一大堆却因连接器不可用而失败</li>
     *   <li><b>风险确认</b>：write/critical 未带 confirmed 时返回提示，等用户下一轮带「确认」再来。
     *       <b>必须在幂等占位之前</b>——否则每次「未确认」都会占掉一个幂等键，
     *       用户真正确认时反被判为重复提交</li>
     *   <li><b>幂等控制</b>：最靠内，因为它依赖前面所有校验都通过后才有意义</li>
     * </ol>
     *
     * <p><b>幂等的两条分支</b>：</p>
     * <ul>
     *   <li>已成功过的相同幂等键 → 直接重放首次结果（{@code source=idempotent}），不重复执行</li>
     *   <li>首次调用 → 先以 {@code success=0} 插占位行抢占执行权。
     *       插入撞唯一索引即表示并发请求正在执行，提示勿重复提交（{@code source=idempotent}）</li>
     * </ul>
     *
     * @param dto 工具调用请求
     * @return 调用结果；各失败分支通过 {@code source} 字段区分原因
     */
    @Transactional
    public ToolInvokeResultDTO invoke(ToolInvokeDTO dto) {
        if (dto == null) {
            throw new BusinessException("工具调用请求不能为空");
        }
        // ========== 关卡 1：工具查找 ==========
        OpenTool tool = findTool(dto);
        if (tool == null) {
            // 找不到工具不落库：连工具都不存在的请求多半是调用方传错，不是真实业务调用
            log.warn("工具未注册 toolName={} intentBind={}", dto.getToolName(), dto.getIntentBind());
            return fail(null, "未找到可用的开放工具", "registry");
        }
        // ========== 关卡 2：行业包开关 ==========
        if (StringUtils.hasText(tool.getPackCode())) {
            OpenPack pack = packMapper.selectOne(new LambdaQueryWrapper<OpenPack>()
                    .eq(OpenPack::getCode, tool.getPackCode()).last("limit 1"));
            if (pack == null || pack.getEnabled() == null || pack.getEnabled() != 1) {
                log.info("工具所属行业包未启用 tool={} packCode={}", tool.getName(), tool.getPackCode());
                return fail(tool.getName(), "该功能所属行业包未启用", "pack-off");
            }
        }
        // ========== 关卡 3：连接器可用性 ==========
        OpenConnector connector = connectorMapper.selectById(tool.getConnectorId());
        if (connector == null) {
            throw new BusinessException("工具绑定的连接器不存在 tool=" + tool.getName());
        }
        if (connector.getEnabled() == null || connector.getEnabled() != 1) {
            throw new BusinessException("工具绑定的连接器已停用 tool=" + tool.getName());
        }

        // ========== 关卡 4：风险确认 ==========
        if (needConfirm(tool) && !Boolean.TRUE.equals(dto.getConfirmed())) {
            // 不落幂等占位行：见类注释，未确认的请求不该消耗幂等键
            log.info("工具需确认但未确认 tool={} risk={}", tool.getName(), tool.getRisk());
            return fail(tool.getName(), "该操作涉及重要变更，请确认后再执行。", "confirm-required");
        }

        // ========== 关卡 5：幂等控制 ==========
        String idempotencyKey = StringUtils.hasText(dto.getIdempotencyKey()) ? dto.getIdempotencyKey() : null;
        OpenToolInvoke placeholder = null;
        if (idempotencyKey != null) {
            // 先查是否已成功过：命中则重放首次结果，绝不重复执行（防重复退款/冻卡）
            OpenToolInvoke done = recorder.findSucceeded(idempotencyKey);
            if (done != null) {
                log.info("幂等命中，重放首次结果 tool={} key={}", tool.getName(), idempotencyKey);
                ToolInvokeResultDTO replay = new ToolInvokeResultDTO();
                replay.setSuccess(true);
                replay.setToolName(tool.getName());
                replay.setOutput(done.getResponseJson());
                replay.setSource("idempotent");
                return replay;
            }
            // 独立事务插入占位行：撞唯一索引时只回滚这一小段，
            // 不会把主链路事务标记为 rollback-only（否则下面正常返回也会抛 UnexpectedRollbackException）
            placeholder = recorder.tryInsertPlaceholder(tool, dto, idempotencyKey);
            if (placeholder == null) {
                // 撞唯一索引：另一个并发请求已抢占，此时不能再执行
                return fail(tool.getName(), "请求正在处理中，请勿重复提交。", "idempotent");
            }
        }
        // ========== 分发执行 ==========
        String output;
        try {
            output = "MOCK".equalsIgnoreCase(connector.getType())
                    ? invokeMock(tool, dto)
                    : invokeRest(tool, connector, dto);
        } catch (Exception e) {
            // 失败留痕走独立事务，否则接下来的 throw 会把主事务连同这条记录一起回滚，
            // 线上就查不到任何失败痕迹
            log.warn("工具执行失败 tool={} sessionId={}", tool.getName(), dto.getSessionId(), e);
            recorder.writeResult(placeholder, tool, dto, null, e.getMessage(), 0);
            throw e;
        }

        recorder.writeResult(placeholder, tool, dto, output, null, 1);
        ToolInvokeResultDTO result = new ToolInvokeResultDTO();
        result.setSuccess(true);
        result.setToolName(tool.getName());
        result.setOutput(output);
        // 有幂等键走的是「真实执行」，无幂等键同样如此；只有重放才标 idempotent
        result.setSource("MOCK".equalsIgnoreCase(connector.getType()) ? "mock" : "real");
        return result;
    }

    /**
     * 工具查找：优先按 toolName 精确匹配，其次按 intentBind 匹配。
     *
     * <p>两种入口对应两类调用方：显式调用（对方知道工具名）与意图驱动（Agent 只识别出意图）。
     * 按 intentBind 查找时要过滤启用状态，避免查到已下线的工具。</p>
     */
    private OpenTool findTool(ToolInvokeDTO dto) {
        if (StringUtils.hasText(dto.getToolName())) {
            return this.getOne(new LambdaQueryWrapper<OpenTool>()
                    .eq(OpenTool::getName, dto.getToolName().trim()).last("limit 1"));
        }
        if (StringUtils.hasText(dto.getIntentBind())) {
            return this.getOne(new LambdaQueryWrapper<OpenTool>()
                    .eq(OpenTool::getIntentBind, dto.getIntentBind().trim())
                    .orderByAsc(OpenTool::getId).last("limit 1"));
        }
        return null;
    }

    /**
     * 是否必须用户确认。
     *
     * <p>read 级只读取数据，无需确认；write/critical 会改对方系统状态，
     * 必须用户显式确认。判定放在这里而不是依赖调用方传值，是因为
     * 「工具风险等级」是服务端配置，不该由调用方决定是否需要确认。</p>
     */
    private boolean needConfirm(OpenTool tool) {
        String risk = tool.getRisk() == null ? "" : tool.getRisk().trim().toLowerCase(Locale.ROOT);
        return "write".equals(risk) || "critical".equals(risk);
    }

    /**
     * MOCK 连接器：返回演示数据，不触达任何外部系统
     */
    private String invokeMock(OpenTool tool, ToolInvokeDTO dto) {
        log.info("MOCK 连接器返回演示数据 tool={} sessionId={}", tool.getName(), dto.getSessionId());
        return "{\"mock\":true,\"tool\":\"" + tool.getName()
                + "\",\"message\":\"演示数据，未调用真实业务系统\"}";
    }

    /**
     * 构造统一格式的失败结果
     */
    private ToolInvokeResultDTO fail(String toolName, String message, String source) {
        ToolInvokeResultDTO result = new ToolInvokeResultDTO();
        result.setSuccess(false);
        result.setToolName(toolName);
        result.setOutput(message);
        result.setSource(source);
        return result;
    }

    /**
     * REST 连接器出站执行：真实发起 HTTP 调用并把响应交给调用方。
     *
     * <p><b>为什么不用共享 RestTemplate 单例</b>：每个工具的 {@code timeoutMs} 不同，
     * 而超时是配置在 {@code RequestFactory} 上的。复用一个实例会让所有工具被迫用同一超时，
     * 改一个影响全部；这里按工具超时即时构造，代价可忽略（真实场景是「一次调用」不是高频循环）。</p>
     *
     * <p><b>三重安全约束（缺一不可）</b>：</p>
     * <ol>
     *   <li><b>禁跟随重定向</b>：对方返回 302 跳到内网地址时，若自动跟随就等于绕过 SSRF 校验。
     *       校验只发生在出站前那一刻的 URL 上，重定向后是全新 URL，必须重新过校验——
     *       而更简单的做法是直接禁止跟随，把 3xx 当作业务异常返回</li>
     *   <li><b>出站前再次 assertSafe</b>：保存连接器时已校验过一遍，但运行期 baseUrl 可能被改
     *       （DB 直改、历史脏数据），出站前重校验才真正守得住</li>
     *   <li><b>query 型鉴权参数拼在 URL 上，随后从 headers 摘除</b>：见 {@link #buildUrl}，
     *       避免 {@code X-Internal-Auth-Query} 这个内部约定头被真的发到对方系统</li>
     * </ol>
     *
     * <p><b>返回值约定</b>：返回对方响应体字符串；调用方（{@code invoke}）负责包成
     * {@code ToolInvokeResultDTO}。空响应体返回空串而非 null，避免上层 NPE。</p>
     *
     * @param tool      工具配置（提供 httpMethod / httpPath / timeoutMs）
     * @param connector 连接器配置（提供 baseUrl / authJson）
     * @param dto       工具调用入参
     * @return 对方系统响应体
     */
    private String invokeRest(OpenTool tool, OpenConnector connector, ToolInvokeDTO dto) {
        Map<String, String> headers = new HashMap<>();
        // 1) 注入鉴权头。bearer/basic/header 直接写进 headers；
        //    query 型写进 AUTH_QUERY_HEADER 约定 key，由 buildUrl 消费
        applyConnectorAuth(headers, connector);

        // 2) 组装 URL。注意必须在 assertSafe 之前完成：
        //    query 型鉴权参数是 URL 的一部分，漏拼会让「校验的 URL」与「真正请求的 URL」不一致
        String url = buildUrl(connector, tool, headers);

        // 3) SSRF 防护：出站前重校验。connector.getBaseUrl() 作为租户白名单的匹配依据
        ConnectorUrlGuard.assertSafe(url, connector.getBaseUrl());

        // 4) 请求体：按连接器字段映射把入参反向转成对方要的结构
        String body = buildRequestBody(tool, dto);
        // 5) HTTP 方法：缺省 POST（大多数业务查询/变更都是 POST）
        HttpMethod method = resolveHttpMethod(tool.getHttpMethod());
        try {
            HttpEntity<String> entity = new HttpEntity<>(body, toSpringHeaders(headers));
            ResponseEntity<String> response = buildRestTemplate(tool.getTimeoutMs()).exchange(
                    URI.create(url), method, entity, String.class);
            String responseBody = response.getBody();
            log.info("REST 连接器调用完成 tool={} url={} status={}",
                    tool.getName(), url, response.getStatusCode().value());
            // 3xx：因为禁止跟随重定向，Spring 会把重定向响应原样交出。而对方系统用 3xx
            // 表达「正常结果」是不合常理的，一律按异常处理，防止有人借重定向绕过 SSRF 校验
            if (response.getStatusCode().is3xxRedirection()) {
                throw new BusinessException("连接器返回重定向，已拒绝跟随以保证安全 tool=" + tool.getName());
            }
            if (response.getStatusCode().isError()) {
                // 4xx/5xx：把状态码带出去，便于排查是「密钥错」还是「对方服务挂了」
                throw new BusinessException("连接器返回错误状态 tool=" + tool.getName()
                        + " status=" + response.getStatusCode().value());
            }
            return responseBody == null ? "" : responseBody;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 网络超时/连接拒绝等：统一转业务异常，由上层决定是否重试。
            // 注意不能用 catch(Exception) 吞掉上面的 BusinessException，故先单独 rethrow
            log.warn("REST 连接器调用失败 tool={} url={}", tool.getName(), url, e);
            throw new BusinessException("连接器调用失败，请稍后重试");
        }
    }

    /**
     * 构造 RestTemplate：按工具超时即时创建，并禁止跟随重定向。
     *
     * <p><b>为什么禁止重定向</b>：SSRF 校验只针对出站前那个 URL。若自动跟随，
     * 对方（或中间人）返回 {@code 302 Location: http://169.254.169.254/latest/meta-data/}
     * 就能把请求引到云元数据服务，校验形同虚设。</p>
     */
    private RestTemplate buildRestTemplate(Integer timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 超时下限 500ms 由 saveTool 保证；此处再兜一层，防止历史数据脏值
        int timeout = (timeoutMs == null || timeoutMs < 500) ? 5000 : timeoutMs;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        // 关键：不跟随重定向。SimpleClientHttpRequestFactory 默认即不跟随
        // （setFollowRedirects 仅在部分版本可用），这里显式声明意图
        return new RestTemplate(factory);
    }

    /**
     * 把普通 Map 转成 Spring 的 HttpHeaders（Content-Type 默认 JSON）
     */
    private HttpHeaders toSpringHeaders(Map<String, String> headers) {
        HttpHeaders springHeaders = new HttpHeaders();
        headers.forEach(springHeaders::set);
        // 未被显式覆盖时用 JSON，对方 REST 接口绝大多数按 JSON 解析
        if (!springHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            springHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return springHeaders;
    }

    /**
     * 组装请求体：按连接器字段映射把入参转成对方要的结构。
     *
     * <p><b>映射为空时为什么直接发原始入参</b>：连接器可能压根没配映射表
     * （对方接口字段名与本系统一致），此时原样发送才能「配了就能通」。</p>
     *
     * <p><b>但这不是无条件的</b>：若映射表有配置却一条都没取到值，
     * 属于配置错误（字段名拼错、source key 对不上）。两种情况都返回空 map，
     * 无法从返回值区分，故此处统一按「未配置」处理并发 warn 日志留痕——
     * 静默发送原始入参会让对方返回 200 但业务取不到值，排查成本极高。</p>
     */
    private String buildRequestBody(OpenTool tool, ToolInvokeDTO dto) {
        Map<String, Object> source = buildSourceData(dto);
        Map<String, Object> mapped = fieldMappingService.reverseMapping(
                tool.getConnectorId(), source);
        if (mapped == null || mapped.isEmpty()) {
            log.warn("连接器无有效字段映射，按原始入参发送 connectorId={} tool={}",
                    tool.getConnectorId(), tool.getName());
            return JSON.toJSONString(source);
        }
        return JSON.toJSONString(mapped);
    }

    /**
     * 把工具调用入参整理成「字段映射」的输入结构。
     * <p>映射表的 targetField 与这里的 key 对应，因此 key 名需与配置保持一致。</p>
     */
    private Map<String, Object> buildSourceData(ToolInvokeDTO dto) {
        Map<String, Object> source = new HashMap<>();
        source.put("sessionId", dto.getSessionId());
        source.put("entityId", dto.getEntityId());
        source.put("entityType", dto.getEntityType());
        source.put("intentBind", dto.getIntentBind());
        source.put("confirm", dto.getConfirmed());
        return source;
    }

    /**
     * 解析 HTTP 方法，非法或缺失时按 POST 兜底
     */
    private HttpMethod resolveHttpMethod(String httpMethod) {
        if (!StringUtils.hasText(httpMethod)) {
            return HttpMethod.POST;
        }
        try {
            return HttpMethod.valueOf(httpMethod.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 配置了非法方法名：不可静默降级为 GET（GET 语义是只读，
            // 把 write 工具降级成 GET 会绕过风险确认的预期），
            // 也不能直接崩，故按 POST 兜底并留日志
            log.warn("工具配置了非法 HTTP 方法，按 POST 兜底 method={}", httpMethod);
            return HttpMethod.POST;
        }
    }

    /**
     * 拼接最终 URL，并把 query 型鉴权参数附上
     */
    private String buildUrl(OpenConnector connector, OpenTool tool, Map<String, String> headers) {
        String base = StringUtils.hasText(connector.getBaseUrl()) ? connector.getBaseUrl().trim() : "";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = StringUtils.hasText(tool.getHttpPath()) ? tool.getHttpPath().trim() : "";
        if (StringUtils.hasText(path) && !path.startsWith("/")) {
            path = "/" + path;
        }
        String url = base + path;

        // query 型鉴权：追加到 URL；拼完立即从 headers 摘掉，避免误发
        String authQuery = headers.remove(AUTH_QUERY_HEADER);
        if (StringUtils.hasText(authQuery)) {
            url += (url.contains("?") ? "&" : "?") + authQuery;
        }
        return url;
    }

    /**
     * 注入连接器鉴权信息到出站请求。
     *
     * <p><b>为什么必须做</b>：真实 REST 连接器（对方 OMS/CRM）几乎都要求鉴权。
     * 不注入头，对方一律返回 401——这是除 MOCK 外所有真实连接器跑不通的直接原因。</p>
     *
     * <p><b>支持的鉴权类型</b>（按 authJson.type 分派）：</p>
     * <table border="1">
     *   <tr><th>type</th><th>authJson 示例</th><th>注入效果</th></tr>
     *   <tr><td>bearer</td><td>{"type":"bearer","token":"xxx"}</td><td>Authorization: Bearer xxx</td></tr>
     *   <tr><td>basic</td><td>{"type":"basic","username":"u","password":"p"}</td><td>Authorization: Basic base64(u:p)</td></tr>
     *   <tr><td>header</td><td>{"type":"header","name":"X-Api-Key","value":"xxx"}</td><td>X-Api-Key: xxx</td></tr>
     *   <tr><td>query</td><td>{"type":"query","name":"apikey","value":"xxx"}</td><td>URL 追加 ?apikey=xxx</td></tr>
     * </table>
     *
     * <p><b>安全约束</b>：</p>
     * <ul>
     *   <li>authJson 落库时为 AES-GCM 密文，此处先解密（SecretCipherUtil.decrypt）</li>
     *   <li>解密失败或 JSON 非法时<b>抛异常而非静默跳过</b>——静默跳过会让请求裸奔出站，
     *       对方可能把无鉴权请求当作合法调用，酿成越权</li>
     *   <li>日志中<b>绝不打印 token 明文</b>，只打印类型与目标 host</li>
     * </ul>
     *
     * @param headers   出站请求头（会被就地修改）
     * @param connector 连接器配置
     */
    private void applyConnectorAuth(Map<String, String> headers, OpenConnector connector) {
        // 未配置鉴权视为「对方无需鉴权」，直接返回
        if (connector == null || !StringUtils.hasText(connector.getAuthJson())) {
            return;
        }
        String plain = SecretCipherUtil.decrypt(connector.getAuthJson());
        if (!StringUtils.hasText(plain)) {
            throw new BusinessException("连接器鉴权信息解密失败 connectorId=" + connector.getId());
        }
        JSONObject auth;
        try {
            auth = JSON.parseObject(plain);
        } catch (Exception e) {
            log.warn("连接器鉴权配置解析失败 connectorId={}", connector.getId(), e);
            // authJson 不是合法 JSON：配置错误，必须暴露而不是静默降级
            throw new BusinessException("连接器鉴权配置格式错误 connectorId=" + connector.getId());
        }
        if (auth == null || !StringUtils.hasText(auth.getString("type"))) {
            throw new BusinessException("连接器鉴权缺少 type 字段 connectorId=" + connector.getId());
        }
        String type = auth.getString("type").trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "bearer" -> {
                String token = requireAuthField(auth, "token", connector.getId());
                headers.put("Authorization", "Bearer " + token);
            }
            case "basic" -> {
                String username = auth.getString("username");
                String password = auth.getString("password");
                if (!StringUtils.hasText(username)) {
                    throw new BusinessException("连接器 basic 鉴权缺少 username connectorId=" + connector.getId());
                }
                // 标准 Basic：base64(username:password)，password 允许为空
                String raw = username + ":" + (password == null ? "" : password);
                String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
            case "header" -> {
                String name = requireAuthField(auth, "name", connector.getId());
                String value = requireAuthField(auth, "value", connector.getId());
                headers.put(name, value);
            }
            case "query" -> {
                // query 型鉴权需拼到 URL 上，由调用方处理；这里把值塞进约定 key 供后续拼接
                String name = requireAuthField(auth, "name", connector.getId());
                String value = requireAuthField(auth, "value", connector.getId());
                headers.put(AUTH_QUERY_HEADER, name + "=" + urlEncode(value));
            }
            default -> throw new BusinessException(
                    "不支持的鉴权类型: " + type + "（支持 bearer/basic/header/query）");
        }
        // 只记类型与目标，不记密钥值
        log.debug("连接器鉴权已注入 type={} connectorId={} baseUrl={}",
                type, connector.getId(), connector.getBaseUrl());
    }

    /**
     * 读取必填鉴权字段，缺失时抛出明确错误
     */
    private String requireAuthField(JSONObject auth, String field, Long connectorId) {
        String value = auth.getString(field);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("连接器鉴权缺少 " + field + " 字段 connectorId=" + connectorId);
        }
        return value;
    }

    /**
     * URL 参数编码（用于 query 型鉴权）。
     *
     * <p>必须编码：密钥里常含 {@code + / = &} 等字符，直接拼接会被对方解析成
     * 另一个参数或截断，导致鉴权失败且难以排查。空值返回空串，由调用方决定是否拼接。</p>
     *
     * @param value 原始值
     * @return application/x-www-form-urlencoded 编码结果
     */
    private String urlEncode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
