package com.ai.cs.base.support;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 按性别从内置默认头像中随机选取。系统图缺失时退回灰色剪影。
 */
public final class DefaultAvatarPicker {

    /** 灰色剪影兜底头像（性别未知或系统图缺失时使用） */
    public static final String SILHOUETTE = "/default-avatar.svg";

    /** 男性内置默认头像池 */
    static final String[] MALE = {
            "/avatars/male/01.png",
            "/avatars/male/02.png",
            "/avatars/male/03.png"
    };

    /** 女性内置默认头像池 */
    static final String[] FEMALE = {
            "/avatars/female/01.png",
            "/avatars/female/02.png",
            "/avatars/female/03.png"
    };

    private DefaultAvatarPicker() {
    }

    /**
     * 判断头像是否为系统默认头像（空值与剪影也算默认）
     */
    public static boolean isSystemDefault(String avatar) {
        if (avatar == null || avatar.isBlank() || SILHOUETTE.equals(avatar.trim())) {
            return true;
        }
        String value = avatar.trim();
        return contains(MALE, value) || contains(FEMALE, value);
    }

    /**
     * 判断头像是否与性别匹配（属于对应性别的默认头像池）
     */
    public static boolean matchesGender(String avatar, Integer gender) {
        if (avatar == null || avatar.isBlank()) {
            return false;
        }
        return contains(poolOf(gender), avatar.trim());
    }

    /**
     * 按性别随机取一个默认头像；性别未知时返回剪影兜底
     */
    public static String pick(Integer gender) {
        String[] pool = poolOf(gender);
        if (pool.length == 0) {
            return SILHOUETTE;
        }
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    /** 判断值是否在池中 */
    private static boolean contains(String[] pool, String value) {
        for (String item : pool) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 按性别取头像池：1-男 2-女，其它返回空池 */
    private static String[] poolOf(Integer gender) {
        if (gender != null && gender == 1) {
            return MALE;
        }
        if (gender != null && gender == 2) {
            return FEMALE;
        }
        return new String[0];
    }
}
