package com.usn.labhub.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.usn.labhub.user.domain.dto.LoginDTO;
import com.usn.labhub.user.domain.dto.MemberQueryDTO;
import com.usn.labhub.user.domain.dto.MemberSaveDTO;
import com.usn.labhub.user.domain.dto.MemberUpdateDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.domain.vo.MemberVO;

public interface ISysUserService extends IService<SysUser> {

    LoginVO login(LoginDTO loginDTO);

    IPage<MemberVO> pageMembers(MemberQueryDTO queryDTO);

    void saveMember(MemberSaveDTO saveDTO);

    void updateMember(MemberUpdateDTO updateDTO);

    void updateMemberStatus(Long id, Byte status);
}
