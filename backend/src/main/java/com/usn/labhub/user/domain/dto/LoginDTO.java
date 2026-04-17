package com.usn.labhub.user.domain.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "用户登录请求参数")
public class LoginDTO {

    @Schema(description = "学号/工号", example = "admin001")
    @NotBlank(message = "账号不能为空")
    private String memberId;

    @Schema(description = "登录密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "账号身份标识", example = "admin")
    private String roleKey; // 改用 roleKey(admin/student)，比 1/2 更直观，且对应数据库 sys_role 表
}
