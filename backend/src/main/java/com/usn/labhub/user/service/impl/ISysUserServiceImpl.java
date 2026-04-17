package com.usn.labhub.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.usn.labhub.user.common.utils.JwtUtils;
import com.usn.labhub.user.domain.dto.LoginDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.mapper.SysUserMapper;
import com.usn.labhub.user.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ISysUserServiceImpl
        extends ServiceImpl<SysUserMapper, SysUser>
        implements ISysUserService {
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SysUserMapper userMapper;
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        Map<String, Object> userMap = userMapper.selectRole(loginDTO.getMemberId());
        //1. 账号与密码校验
        // 1.2 调用 MP 内置的 getOne 方法
        // getOne(wrapper) 会返回一条记录，如果查到多条会报错（正好对应我们的唯一索引）

        // 1.3 基础校验
        if (userMap == null) {
            throw new RuntimeException("该用户不存在");
        }
        String dbPassword = (String) userMap.get("password");
        if (!passwordEncoder.matches(loginDTO.getPassword(), dbPassword)) {
            throw new RuntimeException("密码错误");
        }
        //2. 身份和权限核实(用学号查身份，返回身份)

        String roleKey = (String) userMap.get("roleKey");
        //3. 组装用户信息  4. 签发令牌与考勤
        String token = jwtUtils.createToken(loginDTO.getMemberId(), roleKey);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        LoginVO.UserInfo userInfo = new LoginVO.UserInfo();
        userInfo.setUsername((String) userMap.get("username"));
        userInfo.setMemberId((String) userMap.get("member_id"));
        userInfo.setRole(roleKey);// 以后管理员多了，这个 roleKey 可能是 'super_admin', 'lab_admin' 等
        userInfo.setFacultyName((String) userMap.get("collegeName"));
        userInfo.setMajorName((String) userMap.get("majorName"));
        userInfo.setGroupName((String) userMap.get("groupName"));
        userInfo.setIdentity((String) userMap.get("identityName"));
        vo.setUser(userInfo);
        vo.setAttendance(new LoginVO.AttendanceInfo()); // 考勤先放空

        return vo;
    }
}
