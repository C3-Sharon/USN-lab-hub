package com.usn.labhub.user.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户登录成功返回结果")
public class LoginVO {

    @Schema(description = "JWT身份令牌")
    private  String token;

    @Schema(description = "用户信息")
    private UserInfo user; // 这是一个内部类对象

    @Schema(description = "考勤信息")
    private AttendanceInfo attendance;

    /**
     * 用户基础信息内部类
     * 必须是 static，否则 JSON 转换会报错
     */
    @Data
    public static class UserInfo {
        @Schema(description = "真实姓名")
        private String username;

        @Schema(description = "学号/工号")
        private String memberId;

        @Schema(description = "角色标识 (admin/student)")
        private String role;

        @Schema(description = "身份 (本科/硕士/博士/老师)")
        private String identity;

        @Schema(description = "所属工作组")
        private String groupName;

        @Schema(description = "学院名称")
        private String facultyName;

        @Schema(description = "专业名称")
        private String majorName;
    }

    /**
     * 用户考勤信息内部类
     */
    @Data
    public static class AttendanceInfo {
        @Schema(description = "今日状态：0-未签到, 1-已签到, 2-已签退")
        private Integer todayStatus;

        @Schema(description = "今日签到时间 (HH:mm)")
        private String checkInTime;

        @Schema(description = "本周累计时长 (小时)")
        private Double weekHours;

        @Schema(description = "本学期累计时长 (小时)")
        private Double semesterHours;
    }
}
