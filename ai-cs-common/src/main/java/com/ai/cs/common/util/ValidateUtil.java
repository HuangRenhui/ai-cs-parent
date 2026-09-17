package com.ai.cs.common.util;

import com.ai.cs.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用格式校验。可选项留空视为通过，填了则必须合法。
 */
public final class ValidateUtil {

    /** 工具类禁止实例化 */
    private ValidateUtil() {
    }

    /** 大陆手机号：1 开头 + 3-9 号段 + 共 11 位 */
    public static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    /** 通用邮箱格式 */
    public static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** 头像地址白名单：http(s) 外链 / 已上传头像 / 内置默认头像，防止任意路径注入 */
    public static final Pattern AVATAR = Pattern.compile(
            "^(https?://.+|/files/avatars/[A-Za-z0-9._-]+|/avatars/(male|female)/[0-9]{2}\\.png|/default-avatar\\.svg)$",
            Pattern.CASE_INSENSITIVE);
    /** 账号：字母开头，4-32 位字母/数字/下划线 */
    public static final Pattern ACCOUNT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,31}$");
    /** 昵称/姓名：中文、字母、数字、下划线、间隔号、短横线，1-32 字符 */
    public static final Pattern NICKNAME = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_·\\-]{1,32}$");
    /** 标签：中文、字母、数字、下划线、短横线，1-20 字符 */
    public static final Pattern TAG = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_\\-]{1,20}$");
    /** 会话ID：字母/数字/下划线/短横线，4-64 字符 */
    public static final Pattern SESSION_ID = Pattern.compile("^[A-Za-z0-9_\\-]{4,64}$");

    /** 允许的工单类型（与前端选项一致） */
    private static final Set<String> ORDER_TYPES = Set.of("咨询", "投诉", "建议", "退款");

    /** 去空白；空白串（含 null）归一为 null，便于"留空视为不填"的语义统一 */
    public static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /** 手机号必填校验 */
    public static void requireMobile(String phone) {
        String value = trimToNull(phone);
        if (value == null) {
            throw new BusinessException("手机号不能为空");
        }
        if (!MOBILE.matcher(value).matches()) {
            throw new BusinessException("手机号格式不正确，请输入11位大陆手机号");
        }
    }

    /** 邮箱可选校验：留空通过，填了必须合法且不超 80 字符 */
    public static void optionalEmail(String email) {
        String value = trimToNull(email);
        if (value == null) {
            return;
        }
        if (value.length() > 80 || !EMAIL.matcher(value).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
    }

    /** 头像可选校验：留空通过，填了必须在白名单格式内 */
    public static void optionalAvatar(String url) {
        String value = trimToNull(url);
        if (value == null) {
            return;
        }
        if (value.length() > 500 || !AVATAR.matcher(value).matches()) {
            throw new BusinessException("头像地址不合法");
        }
    }

    /** 性别可选：空/0-未选，1-男，2-女 */
    public static void optionalGender(Integer gender) {
        if (gender == null || gender == 0) {
            return;
        }
        if (gender != 1 && gender != 2) {
            throw new BusinessException("性别取值无效");
        }
    }

    /** URL 可选校验：接受 http/https 链接或已上传头像路径（复用头像白名单） */
    public static void optionalUrl(String url, String fieldName) {
        String value = trimToNull(url);
        if (value == null) {
            return;
        }
        if (value.length() > 500 || !AVATAR.matcher(value).matches() && !value.startsWith("http://") && !value.startsWith("https://")) {
            throw new BusinessException(fieldName + "必须是 http/https 链接或已上传的头像");
        }
    }

    /** 昵称可选校验 */
    public static void optionalNickname(String nickname) {
        String value = trimToNull(nickname);
        if (value == null) {
            return;
        }
        if (!NICKNAME.matcher(value).matches()) {
            throw new BusinessException("昵称仅支持中文、字母、数字、下划线，1-32 个字符");
        }
    }

    /** 标签可选校验 */
    public static void optionalTag(String tag) {
        String value = trimToNull(tag);
        if (value == null) {
            return;
        }
        if (!TAG.matcher(value).matches()) {
            throw new BusinessException("标签仅支持中文、字母、数字，最长 20 个字符");
        }
    }

    /** 账号必填校验 */
    public static void requireAccount(String account) {
        String value = trimToNull(account);
        if (value == null) {
            throw new BusinessException("账号不能为空");
        }
        if (!ACCOUNT.matcher(value).matches()) {
            throw new BusinessException("账号需以字母开头，4-32 位字母数字或下划线");
        }
    }

    /** 姓名必填校验（规则同昵称） */
    public static void requireDisplayName(String name) {
        String value = trimToNull(name);
        if (value == null) {
            throw new BusinessException("姓名不能为空");
        }
        if (!NICKNAME.matcher(value).matches()) {
            throw new BusinessException("姓名仅支持中文、字母、数字，1-32 个字符");
        }
    }

    /**
     * 密码校验
     *
     * @param required true=必填（新增场景），false=留空跳过（编辑场景表示不改密码）
     */
    public static void optionalPassword(String password, boolean required) {
        String value = trimToNull(password);
        if (value == null) {
            if (required) {
                throw new BusinessException("密码不能为空");
            }
            return;
        }
        if (value.length() < 6 || value.length() > 32) {
            throw new BusinessException("密码长度为 6-32 位");
        }
        // 纯数字或纯字母不允许，必须混合
        if (value.chars().allMatch(Character::isDigit) || value.chars().allMatch(Character::isLetter)) {
            throw new BusinessException("密码需同时包含字母和数字");
        }
    }

    /** 必填 + 长度区间校验 */
    public static void requireLength(String value, String fieldName, int min, int max) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(fieldName + "不能为空");
        }
        if (text.length() < min || text.length() > max) {
            throw new BusinessException(fieldName + "长度需在 " + min + "-" + max + " 个字符之间");
        }
    }

    /** 会话ID可选校验 */
    public static void optionalSessionId(String sessionId) {
        String value = trimToNull(sessionId);
        if (value == null) {
            return;
        }
        if (!SESSION_ID.matcher(value).matches()) {
            throw new BusinessException("会话ID格式不正确");
        }
    }

    /** 工单类型必填校验：必须在允许集合内 */
    public static void requireOrderType(String orderType) {
        String value = trimToNull(orderType);
        if (value == null || !ORDER_TYPES.contains(value)) {
            throw new BusinessException("工单类型必须是：咨询、投诉、建议、退款");
        }
    }

    /** 页码兜底：小于 1 按第 1 页 */
    public static int pageNum(int pageNum) {
        return Math.max(pageNum, 1);
    }

    /** 每页条数兜底：非法按 10，上限 100 防止拉全表 */
    public static int pageSize(int pageSize) {
        if (pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
