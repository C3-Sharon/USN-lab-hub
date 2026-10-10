package com.usn.labhub.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.usn.labhub.user.common.auth.AuthException;
import com.usn.labhub.user.common.auth.AuthReason;
import com.usn.labhub.user.common.auth.LoginAccount;
import com.usn.labhub.user.common.utils.JwtUtils;
import com.usn.labhub.user.domain.dto.LoginDTO;
import com.usn.labhub.user.domain.dto.MemberQueryDTO;
import com.usn.labhub.user.domain.dto.MemberSaveDTO;
import com.usn.labhub.user.domain.dto.MemberUpdateDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.domain.vo.MemberVO;
import com.usn.labhub.user.domain.vo.RoleInfoVO;
import com.usn.labhub.user.mapper.SysUserMapper;
import com.usn.labhub.user.service.IAttendanceService;
import com.usn.labhub.user.service.ISysUserService;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class ISysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private static final String MEMBER_ROLE_KEY = "MEMBER";

    private final IAttendanceService attendanceService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper userMapper;

    public ISysUserServiceImpl(IAttendanceService attendanceService, JwtUtils jwtUtils,
                               PasswordEncoder passwordEncoder, SysUserMapper userMapper) {
        this.attendanceService = attendanceService;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        LoginAccount account = userMapper.selectLoginUser(loginDTO.getMemberId());
        if (account == null) {
            throw AuthException.unauthorized(AuthReason.TOKEN_INVALID, "账号或密码错误");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), account.getPassword())) {
            throw AuthException.unauthorized(AuthReason.TOKEN_INVALID, "账号或密码错误");
        }

        Long userId = account.getId();
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw AuthException.unauthorized(AuthReason.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        List<RoleInfoVO> roles = userMapper.selectRolesByUserId(userId).stream()
                .map(this::normalizeRole)
                .toList();
        if (roles.isEmpty()) {
            throw AuthException.forbidden(AuthReason.ACCESS_DENIED, "账号未分配可用角色");
        }
        RoleInfoVO primaryRole = roles.stream()
                .min(Comparator.comparingInt(role -> rolePriority(role.getRoleKey())))
                .orElseThrow();
        String token = jwtUtils.createToken(loginDTO.getMemberId(), primaryRole.getRoleKey(), userId);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        LoginVO.UserInfo userInfo = new LoginVO.UserInfo();
        userInfo.setId(userId);
        userInfo.setUsername(account.getUsername());
        userInfo.setMemberId(account.getMemberId());
        userInfo.setRoles(roles);
        userInfo.setPrimaryRoleKey(primaryRole.getRoleKey());
        userInfo.setPrimaryRoleName(primaryRole.getRoleName());
        userInfo.setRoleKey(primaryRole.getRoleKey());
        userInfo.setRole(primaryRole.getRoleKey());
        userInfo.setFacultyName(account.getCollegeName());
        userInfo.setMajorName(account.getMajorName());
        userInfo.setGroupName(account.getGroupName());
        userInfo.setIdentity(account.getIdentityName());
        vo.setUser(userInfo);
        vo.setAttendance(attendanceService.getOverview(userId));
        return vo;
    }

    @Override
    public IPage<MemberVO> pageMembers(MemberQueryDTO queryDTO) {
        Page<MemberVO> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        return userMapper.selectMemberPage(page, queryDTO);
    }

    @Override
    @Transactional
    public void saveMember(MemberSaveDTO saveDTO) {
        SysUser user = new SysUser();
        BeanUtils.copyProperties(saveDTO, user);
        user.setPassword(passwordEncoder.encode(saveDTO.getMemberId()));
        if (user.getStatus() == null) {
            user.setStatus((byte) 1);
        }
        userMapper.insert(user);
        Integer memberRoleId = userMapper.selectRoleIdByKey(MEMBER_ROLE_KEY);
        if (memberRoleId == null) {
            throw new IllegalStateException("MEMBER role is not initialized");
        }
        userMapper.insertUserRole(user.getId(), memberRoleId);
    }

    @Override
    @Transactional
    public void updateMember(MemberUpdateDTO updateDTO) {
        SysUser user = getById(updateDTO.getId());
        if (user == null) {
            throw new RuntimeException("成员不存在");
        }
        BeanUtils.copyProperties(updateDTO, user);
        updateById(user);
    }

    @Override
    @Transactional
    public void updateMemberStatus(Long id, Byte status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new RuntimeException("状态值非法");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        boolean updated = updateById(user);
        if (!updated) {
            throw new RuntimeException("成员不存在");
        }
    }

    private RoleInfoVO normalizeRole(RoleInfoVO role) {
        return switch (role.getRoleKey()) {
            case "admin" -> new RoleInfoVO("SYSTEM_ADMIN", "系统管理员");
            case "student" -> new RoleInfoVO("MEMBER", "普通成员");
            default -> role;
        };
    }

    private int rolePriority(String roleKey) {
        return switch (roleKey) {
            case "SYSTEM_ADMIN" -> 1;
            case "TEACHER" -> 2;
            case "STOCK_KEEPER" -> 3;
            case "MEMBER" -> 4;
            default -> 99;
        };
    }
}
