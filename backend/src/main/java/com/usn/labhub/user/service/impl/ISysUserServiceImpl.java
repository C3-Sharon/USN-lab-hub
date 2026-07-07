package com.usn.labhub.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.usn.labhub.user.common.utils.JwtUtils;
import com.usn.labhub.user.domain.dto.LoginDTO;
import com.usn.labhub.user.domain.dto.MemberQueryDTO;
import com.usn.labhub.user.domain.dto.MemberSaveDTO;
import com.usn.labhub.user.domain.dto.MemberUpdateDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.domain.vo.MemberVO;
import com.usn.labhub.user.mapper.SysUserMapper;
import com.usn.labhub.user.service.IAttendanceService;
import com.usn.labhub.user.service.ISysUserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ISysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private static final int STUDENT_ROLE_ID = 2;

    @Autowired
    private IAttendanceService attendanceService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysUserMapper userMapper;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        Map<String, Object> userMap = userMapper.selectRole(loginDTO.getMemberId());
        if (userMap == null) {
            throw new RuntimeException("该用户不存在");
        }
        String dbPassword = (String) userMap.get("password");
        if (!passwordEncoder.matches(loginDTO.getPassword(), dbPassword)) {
            throw new RuntimeException("密码错误");
        }

        String roleKey = (String) userMap.get("roleKey");
        Long userId = Long.valueOf(userMap.get("id").toString());
        String token = jwtUtils.createToken(loginDTO.getMemberId(), roleKey, userId);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        LoginVO.UserInfo userInfo = new LoginVO.UserInfo();
        userInfo.setUsername((String) userMap.get("username"));
        userInfo.setMemberId((String) userMap.get("member_id"));
        userInfo.setRole(roleKey);
        userInfo.setFacultyName((String) userMap.get("collegeName"));
        userInfo.setMajorName((String) userMap.get("majorName"));
        userInfo.setGroupName((String) userMap.get("groupName"));
        userInfo.setIdentity((String) userMap.get("identityName"));
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
        save(user);
        userMapper.insertUserRole(user.getId(), STUDENT_ROLE_ID);
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
}
