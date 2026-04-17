package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.LoginDTO;
import com.usn.labhub.user.domain.vo.LoginVO;
import com.usn.labhub.user.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/usnhub/user")
@Tag(name="用户管理接口",description = "包含登录，用户信息查询等")
public class SysUserController {

    @Autowired
    private ISysUserService userService;

    /**
     * 用户登录接口
     * @param loginDTO 登录参数（学号、密码、角色标识）
     * @return 包含 Token、用户信息和考勤概览的 Result
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Validated @RequestBody LoginDTO loginDTO) {

        LoginVO loginVO = userService.login(loginDTO);

        return Result.success(loginVO);
    }
}
