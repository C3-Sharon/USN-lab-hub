package com.usn.labhub.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.usn.labhub.user.domain.dto.LoginDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;


public interface ISysUserService extends IService<SysUser> {
    /**
     * 用户登录业务
     * @param loginDTO 登录信息
     * @return 登录成功后的完整数据包
     */
    LoginVO login(LoginDTO loginDTO);
}
