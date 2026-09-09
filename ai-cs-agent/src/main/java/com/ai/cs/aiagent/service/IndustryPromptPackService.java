package com.ai.cs.aiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行业提示词包服务：电商/零售/金融系统提示词与拒答清单
 * 补全功能清单 P1 缺失「行业提示词包」
 * 开包加载人设/拒答/槽位，不写死电商
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class IndustryPromptPackService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    private static final String PACK_KEY_PREFIX = "industry:prompt:pack:";

    private final Map<String, PromptPack> builtInPacks = new ConcurrentHashMap<>();

    public IndustryPromptPackService() {
        initBuiltInPacks();
    }

    private void initBuiltInPacks() {
        PromptPack ecommerce = new PromptPack();
        ecommerce.setPackCode("ecommerce");
        ecommerce.setPackName("电商");
        ecommerce.setSystemPrompt("你是电商智能客服，擅长处理订单查询、物流跟踪、退换货、售后等问题。语气亲切专业，优先引导自助操作。");
        ecommerce.setRefuseList("""
                1. 不直接执行退款，需用户确认后走退款流程
                2. 不透露其他客户订单信息
                3. 不承诺具体到货时间（物流不可控）
                4. 不处理支付密码/银行卡相关请求
                5. 不提供内部系统操作细节
                """.stripIndent());
        ecommerce.setSlotHints(Map.of(
                "orderId", "请提供订单编号（通常为10-20位数字字母组合）",
                "phone", "请提供收货手机号后四位",
                "reason", "请简要说明退款/换货原因"
        ));
        builtInPacks.put("ecommerce", ecommerce);

        PromptPack retail = new PromptPack();
        retail.setPackCode("retail");
        retail.setPackName("新零售");
        retail.setSystemPrompt("你是新零售智能客服，覆盖门店服务、会员权益、私域运营、履约配送等场景。语气亲切，善用门店就近推荐。");
        retail.setRefuseList("""
                1. 不泄露其他会员信息
                2. 不直接修改会员等级/积分
                3. 不承诺门店库存（实时性不可控）
                4. 不处理支付敏感操作
                """.stripIndent());
        retail.setSlotHints(Map.of(
                "storeId", "请提供门店编号或门店名称",
                "memberPhone", "请提供会员手机号",
                "skuCode", "请提供商品编码"
        ));
        builtInPacks.put("retail", retail);

        PromptPack finance = new PromptPack();
        finance.setPackCode("finance");
        finance.setPackName("金融");
        finance.setSystemPrompt("你是金融智能客服，处理账户查询、理财咨询、信用卡服务、贷款咨询等。语气严谨专业，严格遵守合规要求，敏感操作需二次确认。");
        finance.setRefuseList("""
                1. 绝不直接执行转账/支付/冻卡操作，必须人工复核
                2. 不透露账户余额给非本人
                3. 不提供投资收益承诺或保证
                4. 不处理密码重置请求（引导至安全渠道）
                5. 不讨论内部风控规则
                6. 涉及销户/冻结必须双人复核
                """.stripIndent());
        finance.setSlotHints(Map.of(
                "accountId", "请提供账户号（卡号后四位即可）",
                "idCard", "请提供身份证后六位进行身份验证",
                "amount", "请确认操作金额"
        ));
        builtInPacks.put("finance", finance);

        PromptPack education = new PromptPack();
        education.setPackCode("education");
        education.setPackName("教育");
        education.setSystemPrompt("你是教育智能客服，处理课程咨询、报名缴费、学习进度、证书查询等。语气耐心友好，善用引导式问答。");
        education.setRefuseList("""
                1. 不透露其他学员信息
                2. 不承诺考试通过率
                3. 不直接修改成绩/考勤记录
                """.stripIndent());
        education.setSlotHints(Map.of(
                "courseId", "请提供课程编号或课程名称",
                "studentId", "请提供学员编号",
                "phone", "请提供报名手机号"
        ));
        builtInPacks.put("education", education);
    }

    /**
     * 获取行业提示词包
     *
     * @param packCode 行业包编码（ecommerce/retail/finance/education等）
     * @return 提示词包；未找到时返回null
     */
    public PromptPack getPack(String packCode) {
        if (!StringUtils.hasText(packCode)) {
            return null;
        }
        PromptPack builtIn = builtInPacks.get(packCode);
        if (builtIn != null) {
            return builtIn;
        }
        return loadCustomPack(packCode);
    }

    /**
     * 获取行业包的系统提示词
     */
    public String getSystemPrompt(String packCode) {
        PromptPack pack = getPack(packCode);
        return pack != null ? pack.getSystemPrompt() : null;
    }

    /**
     * 获取行业包的拒答清单
     */
    public String getRefuseList(String packCode) {
        PromptPack pack = getPack(packCode);
        return pack != null ? pack.getRefuseList() : null;
    }

    /**
     * 获取行业包的槽位提示
     */
    public Map<String, String> getSlotHints(String packCode) {
        PromptPack pack = getPack(packCode);
        return pack != null ? pack.getSlotHints() : Map.of();
    }

    /**
     * 保存自定义行业提示词包到Redis
     */
    public void saveCustomPack(PromptPack pack) {
        if (redisTemplate == null || pack == null || !StringUtils.hasText(pack.getPackCode())) {
            return;
        }
        try {
            String key = PACK_KEY_PREFIX + pack.getPackCode();
            redisTemplate.opsForValue().set(key, com.alibaba.fastjson.JSON.toJSONString(pack));
            log.info("保存自定义行业提示词包: {}", pack.getPackCode());
        } catch (Exception e) {
            log.warn("保存行业提示词包失败: {}", e.getMessage());
        }
    }

    private PromptPack loadCustomPack(String packCode) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String key = PACK_KEY_PREFIX + packCode;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return com.alibaba.fastjson.JSON.parseObject(json, PromptPack.class);
            }
        } catch (Exception e) {
            log.warn("加载自定义行业提示词包失败: {}", packCode, e);
        }
        return null;
    }

    /**
     * 行业提示词包
     */
    @lombok.Data
    public static class PromptPack {
        private String packCode;
        private String packName;
        private String systemPrompt;
        private String refuseList;
        private Map<String, String> slotHints = new HashMap<>();
    }
}