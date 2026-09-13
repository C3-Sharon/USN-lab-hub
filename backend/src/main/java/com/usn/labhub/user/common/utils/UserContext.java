package com.usn.labhub.user.common.utils;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
public class UserContext {
    private static final ThreadLocal<String> MEMBER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> ROLE_HOLDER = new ThreadLocal<>();

    public static void setUserContext(Long userId, String memberId, Set<String> roles) {
        USER_ID_HOLDER.set(userId);
        MEMBER_ID_HOLDER.set(memberId);
        ROLE_HOLDER.set(roles == null ? Collections.emptySet() : Set.copyOf(roles));
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static String getMemberId() {
        return MEMBER_ID_HOLDER.get();
    }

    public static Set<String> getRoles() {
        Set<String> roles = ROLE_HOLDER.get();
        return roles == null ? Collections.emptySet() : roles;
    }

    public static void remove() {
        USER_ID_HOLDER.remove();
        MEMBER_ID_HOLDER.remove();
        ROLE_HOLDER.remove();
    }
}
