package com.ai.cs.base.support;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 按性别从内置默认头像中随机选取。系统图缺失时退回灰色剪影。
 */
public final class DefaultAvatarPicker {

    public static final String SILHOUETTE = "/default-avatar.svg";

    static final String[] MALE = {
            "/avatars/male/01.png",
            "/avatars/male/02.png",
            "/avatars/male/03.png"
    };

    static final String[] FEMALE = {
            "/avatars/female/01.png",
            "/avatars/female/02.png",
            "/avatars/female/03.png"
    };

    private DefaultAvatarPicker() {
    }

    public static boolean isSystemDefault(String avatar) {
        if (avatar == null || avatar.isBlank() || SILHOUETTE.equals(avatar.trim())) {
            return true;
        }
        String value = avatar.trim();
        return contains(MALE, value) || contains(FEMALE, value);
    }

    public static boolean matchesGender(String avatar, Integer gender) {
        if (avatar == null || avatar.isBlank()) {
            return false;
        }
        return contains(poolOf(gender), avatar.trim());
    }

    public static String pick(Integer gender) {
        String[] pool = poolOf(gender);
        if (pool.length == 0) {
            return SILHOUETTE;
        }
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private static boolean contains(String[] pool, String value) {
        for (String item : pool) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

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
