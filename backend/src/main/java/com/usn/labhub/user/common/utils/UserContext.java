package com.usn.labhub.user.common.utils;

import org.springframework.stereotype.Component;


@Component
public class UserContext {
    private static final ThreadLocal<String> MEMBER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    public static void setUserContext(Long userId, String memberId) {
        USER_ID_HOLDER.set(userId);
        MEMBER_ID_HOLDER.set(memberId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static String getMemberId() {
        return MEMBER_ID_HOLDER.get();
    }

    public static void remove() {
        USER_ID_HOLDER.remove();
        MEMBER_ID_HOLDER.remove();
    }
}
