package com.usn.labhub.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "成员分页查询参数")
public class MemberQueryDTO {

    @Schema(description = "页码，从 1 开始")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于 0")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于 0")
    private Long pageSize = 10L;

    @Schema(description = "真实姓名，模糊查询")
    private String username;

    @Schema(description = "身份 ID")
    private Integer identityId;

    @Schema(description = "工作组 ID")
    private Integer groupId;

    @Schema(description = "状态，1 正常，0 禁用")
    private Byte status;
}
