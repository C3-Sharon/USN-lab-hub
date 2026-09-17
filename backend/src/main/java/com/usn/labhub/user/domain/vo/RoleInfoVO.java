package com.usn.labhub.user.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "全局角色")
public class RoleInfoVO {

    @Schema(description = "角色标识")
    private String roleKey;

    @Schema(description = "角色名称")
    private String roleName;
}
