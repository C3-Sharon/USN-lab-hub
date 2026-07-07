package com.usn.labhub.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新成员参数")
public class MemberUpdateDTO {

    @Schema(description = "成员 ID")
    @NotNull(message = "成员 ID 不能为空")
    private Long id;

    @Schema(description = "真实姓名")
    @NotBlank(message = "真实姓名不能为空")
    private String username;

    @Schema(description = "学号/工号")
    @NotBlank(message = "学号/工号不能为空")
    private String memberId;

    @Schema(description = "学院专业 ID")
    private Integer facultyId;

    @Schema(description = "身份 ID")
    private Integer identityId;

    @Schema(description = "工作组 ID")
    private Integer groupId;

    @Schema(description = "状态，1 正常，0 禁用")
    private Byte status;
}
