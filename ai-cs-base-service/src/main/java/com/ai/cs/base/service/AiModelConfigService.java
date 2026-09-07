package com.ai.cs.base.service;

import com.ai.cs.base.entity.AiModelConfig;
import com.ai.cs.base.mapper.AiModelConfigMapper;
import com.ai.cs.common.constant.RedisKeyConst;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.llm.AiModelRoute;
import com.ai.cs.common.llm.ModelHealthEnum;
import com.ai.cs.common.llm.ModelProviderEnum;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import com.ai.cs.common.util.SecretCipherUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型注册管理服务：CRUD、设为生效、启停、测试连接、健康状态，并同步 Redis 广播到各消费服务。
 *
 * @author ai-cs
 */
@Slf4j
@Service
public class AiModelConfigService extends ServiceImpl<AiModelConfigMapper, AiModelConfig> {

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private StringRedisTemplate redisTemplate;

    @PostConstruct
    public void init() {
        // 启动时将数据库中启用模型预热到路由池，并写一份 Redis（消费服务冷启动时兜底）
        try {
            List<AiModelConfig> enabled = list(new LambdaQueryWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getEnabled, 1));
            if (enabled != null && !enabled.isEmpty()) {
                publishRegistry();
            }
        } catch (Exception e) {
            log.warn("模型注册表启动预热失败: {}", e.getMessage());
        }
    }

    // ==================== 查询 ====================

    /** 全部模型（按能力分组、优先级升序） */
    public List<AiModelConfig> listAll() {
        List<AiModelConfig> list = this.list(new LambdaQueryWrapper<AiModelConfig>()
                .orderByAsc(AiModelConfig::getModelType, AiModelConfig::getPriority, AiModelConfig::getId));
        list.forEach(this::maskSecret);
        return list;
    }

    /** 某能力已启用模型列表 */
    public List<AiModelConfig> listEnabled(String modelType) {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getEnabled, 1);
        if (StringUtils.hasText(modelType)) {
            wrapper.eq(AiModelConfig::getModelType, modelType);
        }
        List<AiModelConfig> list = this.list(wrapper.orderByAsc(AiModelConfig::getPriority, AiModelConfig::getId));
        list.forEach(this::maskSecret);
        return list;
    }

    /** 某能力当前生效模型 */
    public AiModelConfig getActive(String modelType) {
        AiModelConfig config = this.getOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getIsActive, 1)
                .eq(AiModelConfig::getEnabled, 1)
                .eq(AiModelConfig::getModelType, modelType)
                .last("limit 1"));
        if (config != null) {
            maskSecret(config);
        }
        return config;
    }

    // ==================== 写入 ====================

    /**
     * 新增或修改模型。同能力内仅允许一个生效模型（is_active），由程序保证。
     */
    public void saveModel(AiModelConfig config) {
        if (config == null) {
            throw new BusinessException("模型信息不能为空");
        }
        validate(config);

        ModelTypeEnum typeEnum = ModelTypeEnum.of(config.getModelType());
        if (typeEnum == null) {
            throw new BusinessException("不支持的模型能力类型: " + config.getModelType());
        }
        ModelProviderEnum providerEnum = ModelProviderEnum.of(config.getProvider());
        if (providerEnum == null) {
            throw new BusinessException("不支持的供应方: " + config.getProvider());
        }

        boolean create = config.getId() == null;
        // 校验同能力下名称唯一
        LambdaQueryWrapper<AiModelConfig> dup = new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelName, config.getModelName());
        if (!create) {
            dup.ne(AiModelConfig::getId, config.getId());
        }
        if (this.count(dup) > 0) {
            throw new BusinessException("模型名称[" + config.getModelName() + "]已存在");
        }

        if (create) {
            if (config.getPriority() == null) {
                config.setPriority(0);
            }
            if (config.getEnabled() == null) {
                config.setEnabled(1);
            }
            if (config.getHealth() == null) {
                config.setHealth(ModelHealthEnum.UNKNOWN.getCode());
            }
            // 首条同能力模型默认为生效
            boolean hasActive = this.count(new LambdaQueryWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getIsActive, 1)
                    .eq(AiModelConfig::getModelType, config.getModelType())) > 0;
            if (!hasActive) {
                config.setIsActive(1);
            } else if (config.getIsActive() == null) {
                config.setIsActive(0);
            }
            encryptSecret(config);
            this.save(config);
        } else {
            // 编辑时密钥留空表示不修改，保留原值（前端密钥脱敏后不会回填真实值）
            AiModelConfig db = this.getById(config.getId());
            if (db != null) {
                if (!StringUtils.hasText(config.getApiKey())) {
                    config.setApiKey(db.getApiKey());
                }
                if (!StringUtils.hasText(config.getApiSecret())) {
                    config.setApiSecret(db.getApiSecret());
                }
            }
            encryptSecret(config);
            this.updateById(config);
        }
        // 变更后同步路由与 Redis
        afterChanged(config.getModelType());
    }

    /** 设为生效：同能力其它生效模型置 0，当前置 1 并启用 */
    public void setActive(Long id) {
        AiModelConfig target = requireModel(id);
        // 同能力其它 active 清 0
        this.update(new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelType, target.getModelType())
                .eq(AiModelConfig::getIsActive, 1)
                .ne(AiModelConfig::getId, id)
                .set(AiModelConfig::getIsActive, 0));
        this.update(new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getIsActive, 1)
                .set(AiModelConfig::getEnabled, 1));
        afterChanged(target.getModelType());
    }

    /** 启停模型 */
    public void setEnabled(Long id, boolean enabled) {
        AiModelConfig target = requireModel(id);
        this.update(new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getEnabled, enabled ? 1 : 0));
        // 停用当前生效模型时，把 active 让渡给同能力优先的下一个
        if (!enabled && target.getIsActive() != null && target.getIsActive() == 1) {
            reassignActive(target.getModelType(), id);
        }
        afterChanged(target.getModelType());
    }

    /** 删除模型 */
    public void removeModel(Long id) {
        AiModelConfig target = requireModel(id);
        this.removeById(id);
        if (target.getIsActive() != null && target.getIsActive() == 1) {
            reassignActive(target.getModelType(), id);
        }
        afterChanged(target.getModelType());
    }

    /** 测试连接 */
    public String testConnect(Long id) {
        AiModelConfig config = requireModel(id);
        boolean ok = modelRouter.testConnect(toRoute(config));
        String health = ok ? ModelHealthEnum.HEALTHY.getCode() : ModelHealthEnum.DOWN.getCode();
        this.update(new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getHealth, health));
        afterChanged(config.getModelType());
        return ok ? "连接成功，模型可用" : "连接失败，请检查地址与密钥";
    }

    // ==================== 内部 ====================

    private void afterChanged(String modelType) {
        publishRegistry();
    }

    /** 把某能力启用模型写入 Redis 注册表 + 版本号，并刷新本地路由池 */
    public void publishRegistry() {
        try {
            List<AiModelConfig> allEnabled = this.list(new LambdaQueryWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getEnabled, 1));
            if (redisTemplate != null) {
                // Hash(field=model_type, value=该能力启用模型JSON数组)
                Map<String, String> typeMap = new ConcurrentHashMap<>();
                Map<String, String> effective = new ConcurrentHashMap<>();
                for (AiModelConfig c : allEnabled) {
                    List<AiModelRoute> bucket = new ArrayList<>();
                    String key = c.getModelType();
                    if (typeMap.containsKey(key)) {
                        bucket = JSON.parseArray(typeMap.get(key), AiModelRoute.class);
                    }
                    bucket.add(toRoute(c));
                    typeMap.put(key, JSON.toJSONString(bucket));
                    if (c.getIsActive() != null && c.getIsActive() == 1) {
                        effective.putIfAbsent(key, JSON.toJSONString(toRoute(c)));
                    }
                }
                redisTemplate.opsForHash().putAll(RedisKeyConst.AI_MODEL_REGISTRY, typeMap);
                redisTemplate.opsForHash().putAll(RedisKeyConst.AI_MODEL_ACTIVE, effective);
                redisTemplate.opsForValue().set(RedisKeyConst.AI_MODEL_VERSION,
                        String.valueOf(System.currentTimeMillis()));
            }
        } catch (Exception e) {
            log.warn("同步模型注册表到 Redis 失败: {}", e.getMessage());
        }
        // 刷新本地路由池（与 Redis 解耦）
        List<AiModelRoute> routes = allEnabledForRoute();
        modelRouter.registerLocal(routes);
    }

    private List<AiModelRoute> allEnabledForRoute() {
        List<AiModelConfig> enabled = this.list(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getEnabled, 1));
        List<AiModelRoute> routes = new ArrayList<>(enabled.size());
        for (AiModelConfig c : enabled) {
            routes.add(toRoute(c));
        }
        return routes;
    }

    /** active 模型被删除/停用时，把 active 让渡给同能力优先级次小的启用模型 */
    private void reassignActive(String modelType, Long excludeId) {
        AiModelConfig next = this.getOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelType, modelType)
                .eq(AiModelConfig::getEnabled, 1)
                .ne(AiModelConfig::getId, excludeId)
                .orderByAsc(AiModelConfig::getPriority, AiModelConfig::getId)
                .last("limit 1"));
        if (next != null) {
            this.update(new LambdaUpdateWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getId, next.getId())
                    .set(AiModelConfig::getIsActive, 1));
        }
    }

    private AiModelConfig requireModel(Long id) {
        AiModelConfig config = this.getById(id);
        if (config == null) {
            throw new BusinessException("模型不存在");
        }
        return config;
    }

    private void validate(AiModelConfig c) {
        if (!StringUtils.hasText(c.getModelName())) {
            throw new BusinessException("模型名称不能为空");
        }
        if (!StringUtils.hasText(c.getProvider())) {
            throw new BusinessException("供应方不能为空");
        }
        if (!StringUtils.hasText(c.getModelType())) {
            throw new BusinessException("模型能力类型不能为空");
        }
        if (!StringUtils.hasText(c.getRemoteModel())) {
            throw new BusinessException("上游模型标识不能为空");
        }
    }

    /** 将实体转成路由对象（解密出明文密钥，供测试连接与 Redis 广播使用） */
    public static AiModelRoute toRoute(AiModelConfig c) {
        AiModelRoute r = new AiModelRoute();
        r.setId(c.getId());
        r.setModelName(c.getModelName());
        r.setProvider(c.getProvider());
        r.setModelType(c.getModelType());
        r.setBaseUrl(c.getBaseUrl());
        r.setApiKey(SecretCipherUtil.decrypt(c.getApiKey()));
        r.setApiSecret(SecretCipherUtil.decrypt(c.getApiSecret()));
        r.setRemoteModel(c.getRemoteModel());
        r.setTemperature(c.getTemperature());
        r.setDimension(c.getDimension());
        r.setPriority(c.getPriority());
        r.setTimeoutMs(c.getTimeoutMs());
        r.setFailThreshold(c.getFailThreshold());
        r.setCostPer1kIn(c.getCostPer1kIn());
        r.setCostPer1kOut(c.getCostPer1kOut());
        r.setEnabled(c.getEnabled());
        r.setIsActive(c.getIsActive());
        r.setHealth(c.getHealth());
        return r;
    }

    /** 落库前对敏感字段加密（幂等，密文不再重复加密） */
    private void encryptSecret(AiModelConfig c) {
        if (c == null) {
            return;
        }
        c.setApiKey(SecretCipherUtil.encrypt(c.getApiKey()));
        c.setApiSecret(SecretCipherUtil.encrypt(c.getApiSecret()));
    }

    /** 敏感字段脱敏（先解密再脱敏，避免密文回显给前端） */
    private void maskSecret(AiModelConfig c) {
        if (c == null) {
            return;
        }
        if (StringUtils.hasText(c.getApiKey())) {
            c.setApiKey(mask(SecretCipherUtil.decrypt(c.getApiKey())));
        }
        if (StringUtils.hasText(c.getApiSecret())) {
            c.setApiSecret(mask(SecretCipherUtil.decrypt(c.getApiSecret())));
        }
    }

    private String mask(String secret) {
        if (secret == null || secret.length() <= 6) {
            return "******";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 2);
    }
}
