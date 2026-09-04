package com.ai.cs.ops.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogRedactor {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("\\b[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,18}\\d\\b");
    private static final Pattern SECRET = Pattern.compile("(?i)(authorization|api[-_]?key|secret|password|token|auth)\\s*[:=]\\s*[^\\s,;\"']+");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern SK = Pattern.compile("(?i)\\bsk-[A-Za-z0-9]{8,}");
    private static final Pattern LLM_FIELD = Pattern.compile("(?i)(\"(?:prompt|messages|completion)\"\\s*:\\s*)(\".*?\")");

    public String redact(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String s = SECRET.matcher(text).replaceAll("$1=***");
        s = BEARER.matcher(s).replaceAll("Bearer ***");
        s = SK.matcher(s).replaceAll("sk-***");
        s = LLM_FIELD.matcher(s).replaceAll("$1\"***\"");
        s = ID_CARD.matcher(s).replaceAll("***ID***");
        Matcher phones = PHONE.matcher(s);
        StringBuffer phoneBuf = new StringBuffer();
        while (phones.find()) {
            String p = phones.group(1);
            phones.appendReplacement(phoneBuf, p.substring(0, 3) + "****" + p.substring(7));
        }
        phones.appendTail(phoneBuf);
        s = CARD.matcher(phoneBuf.toString()).replaceAll("***CARD***");
        return s;
    }
}
