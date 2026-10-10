package com.usn.labhub.user.service;

import com.usn.labhub.user.common.auth.AuthException;
import com.usn.labhub.user.common.auth.AuthReason;
import com.usn.labhub.user.common.auth.LoginAccount;
import com.usn.labhub.user.common.utils.JwtUtils;
import com.usn.labhub.user.domain.dto.LoginDTO;
import com.usn.labhub.user.domain.dto.MemberSaveDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.domain.vo.RoleInfoVO;
import com.usn.labhub.user.mapper.SysUserMapper;
import com.usn.labhub.user.service.impl.ISysUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserLoginServiceTest {

    private IAttendanceService attendanceService;
    private JwtUtils jwtUtils;
    private PasswordEncoder passwordEncoder;
    private SysUserMapper userMapper;
    private ISysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        attendanceService = mock(IAttendanceService.class);
        jwtUtils = mock(JwtUtils.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userMapper = mock(SysUserMapper.class);
        service = new ISysUserServiceImpl(attendanceService, jwtUtils, passwordEncoder, userMapper);
    }

    @Test
    void loginReturnsAllRolesAndHighestPriorityPrimaryRole() {
        when(userMapper.selectLoginUser("20260001")).thenReturn(activeUser());
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(userMapper.selectRolesByUserId(2L)).thenReturn(List.of(
                new RoleInfoVO("STOCK_KEEPER", "库存管理员"),
                new RoleInfoVO("TEACHER", "老师/实验室负责人")
        ));
        when(jwtUtils.createToken("20260001", "TEACHER", 2L)).thenReturn("jwt");

        LoginVO result = service.login(loginRequest());

        assertEquals("jwt", result.getToken());
        assertEquals(2, result.getUser().getRoles().size());
        assertEquals("TEACHER", result.getUser().getPrimaryRoleKey());
        assertEquals("TEACHER", result.getUser().getRoleKey());
        assertEquals("TEACHER", result.getUser().getRole());
    }

    @Test
    void disabledAccountCannotLogin() {
        LoginAccount user = activeUser();
        user.setStatus((byte) 0);
        when(userMapper.selectLoginUser("20260001")).thenReturn(user);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        AuthException error = assertThrows(AuthException.class, () -> service.login(loginRequest()));

        assertEquals(AuthReason.ACCOUNT_DISABLED, error.getReason());
        assertEquals(401, error.getHttpStatus());
    }

    @Test
    void newMemberUsesMemberRoleResolvedByKey() {
        MemberSaveDTO request = new MemberSaveDTO();
        request.setMemberId("20260002");
        request.setUsername("新成员");
        when(passwordEncoder.encode("20260002")).thenReturn("hash");
        when(userMapper.selectRoleIdByKey("MEMBER")).thenReturn(2);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(9L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        service.saveMember(request);

        verify(userMapper).insertUserRole(9L, 2);
    }

    private LoginDTO loginRequest() {
        LoginDTO request = new LoginDTO();
        request.setMemberId("20260001");
        request.setPassword("secret");
        return request;
    }

    private LoginAccount activeUser() {
        LoginAccount account = new LoginAccount();
        account.setId(2L);
        account.setUsername("测试成员");
        account.setMemberId("20260001");
        account.setPassword("hash");
        account.setStatus((byte) 1);
        account.setIdentityName("本科生");
        account.setGroupName("物联网开发组");
        account.setCollegeName("计算机学院");
        account.setMajorName("物联网工程");
        return account;
    }
}
