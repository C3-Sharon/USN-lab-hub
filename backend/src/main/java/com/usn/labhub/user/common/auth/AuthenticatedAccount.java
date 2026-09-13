package com.usn.labhub.user.common.auth;

import java.util.Set;

public record AuthenticatedAccount(Long userId, String memberId, Set<String> roles) {
}
