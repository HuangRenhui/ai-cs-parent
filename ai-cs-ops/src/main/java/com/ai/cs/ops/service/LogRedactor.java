package com.ai.cs.ops.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏器：日志对外展示前，对手机号、身份证、银行卡、密钥令牌等敏感信息打码，
 * 防止运营排障页面泄露用户隐私与系统凭证。
 */
@Component
public class LogRedactor {

    /** 手机号：1 开头 11 位，且前后不能再跟数字（避免误伤长数字串中间片段） */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    /** 18 位身份证号（含出生日期段合法性约束） */
    private static final Pattern ID_CARD = Pattern.compile("\\b[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b");
    /** 银行卡号：13~19 位数字，允许空格/短横线分隔 */
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,18}\\d\\b");
    /** key=value 形式的密钥类字段（authorization/apiKey/secret/password/token 等） */
    private static final Pattern SECRET = Pattern.compile("(?i)(authorization|api[-_]?key|secret|password|token|auth)\\s*[:=]\\s*[^\\s,;\"']+");
    /** HTTP Bearer 令牌 */
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");
    /** OpenAI 风格 sk- 开头的 API Key */
    private static final Pattern SK = Pattern.compile("(?i)\\bsk-[A-Za-z0-9]{8,}");
    /** JSON 中的 prompt/messages/completion 大字段：内容可能含整段用户对话，整体打码 */
    private static final Pattern LLM_FIELD = Pattern.compile("(?i)(\"(?:prompt|messages|completion)\"\\s*:\\s*)(\".*?\")");

    /**
     * 对一段日志文本做脱敏，按「凭证 → 证件 → 手机号 → 银行卡」顺序逐类替换。
     * 手机号保留前 3 后 4 位，兼顾排障可读性；其余类型整体替换为占位符。
     *
     * @param text 原始日志文本，空值原样返回
     * @return 脱敏后的文本
     */
    public String redact(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        // 先打码各类密钥/令牌：这类内容泄露危害最大
        String s = SECRET.matcher(text).replaceAll("$1=***");
        s = BEARER.matcher(s).replaceAll("Bearer ***");
        s = SK.matcher(s).replaceAll("sk-***");
        // LLM 请求/响应大字段整体打码（保留字段名便于定位结构）
        s = LLM_FIELD.matcher(s).replaceAll("$1\"***\"");
        // 身份证整体打码
        s = ID_CARD.matcher(s).replaceAll("***ID***");
        // 手机号保留前 3 后 4（如 138****5678），便于与工单/会话核对
        Matcher phones = PHONE.matcher(s);
        StringBuffer phoneBuf = new StringBuffer();
        while (phones.find()) {
            String p = phones.group(1);
            phones.appendReplacement(phoneBuf, p.substring(0, 3) + "****" + p.substring(7));
        }
        phones.appendTail(phoneBuf);
        // 银行卡放最后：其宽松正则可能吞掉前面已打码片段，基于手机号处理结果再替换
        s = CARD.matcher(phoneBuf.toString()).replaceAll("***CARD***");
        return s;
    }
}
