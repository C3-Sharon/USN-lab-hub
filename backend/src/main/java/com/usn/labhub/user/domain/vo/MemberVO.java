package com.usn.labhub.user.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "成员信息")
public class MemberVO {

    @Schema(description = "成员 ID")
    private Long id;

    @Schema(description = "真实姓名")
    private String username;

    @Schema(description = "学号/工号")
    private String memberId;

    @Schema(description = "学院专业 ID")
    private Integer facultyId;

    @Schema(description = "身份 ID")
    private Integer identityId;

    @Schema(description = "工作组 ID")
    private Integer groupId;

    @Schema(description = "学院名称")
    private String collegeName;

    @Schema(description = "专业名称")
    private String majorName;

    @Schema(description = "身份名称")
    private String identityName;

    @Schema(description = "组别名称")
    private String groupName;

    @Schema(description = "状态，1 正常，0 禁用")
    private Byte status;
}
