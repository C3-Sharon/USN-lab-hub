package com.usn.labhub.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "考勤记录")
public class AttendanceQueryVO {

    @Schema(description = "考勤记录 ID")
    private Long id;

    @Schema(description = "姓名")
    private String username;

    @Schema(description = "学号/工号")
    private String memberId;

    @Schema(description = "组别")
    private String groupName;

    @Schema(description = "签到时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkInTime;

    @Schema(description = "签退时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkOutTime;

    @Schema(description = "本次时长，分钟")
    private Integer durationMinutes;

    @Schema(description = "所属学期")
    private String semester;

    @Schema(description = "操作来源")
    private String source;
}
