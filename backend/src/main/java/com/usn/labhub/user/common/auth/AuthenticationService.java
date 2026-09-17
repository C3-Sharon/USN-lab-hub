package com.usn.labhub.user.common.auth;

import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AuthenticationService {

    private final SysUserMapper userMapper;

    public AuthenticationService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public AuthenticatedAccount authenticate(Long userId, String memberId) {
        if (userId == null || memberId == null || memberId.isBlank()) {
            throw AuthException.unauthorized(AuthReason.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !memberId.equals(user.getMemberId())) {
            throw AuthException.unauthorized(AuthReason.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw AuthException.unauthorized(AuthReason.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }

        Set<String> roles = new LinkedHashSet<>();
        userMapper.selectRolesByUserId(userId).forEach(role -> roles.add(normalizeRoleKey(role.getRoleKey())));
        if (roles.isEmpty()) {
            throw AuthException.forbidden(AuthReason.ACCESS_DENIED, "账号未分配可用角色");
        }
        return new AuthenticatedAccount(userId, memberId, Set.copyOf(roles));
    }

    private String normalizeRoleKey(String roleKey) {
        return switch (roleKey) {
            case "admin" -> "SYSTEM_ADMIN";
            case "student" -> "MEMBER";
            default -> roleKey;
        };
    }
}
