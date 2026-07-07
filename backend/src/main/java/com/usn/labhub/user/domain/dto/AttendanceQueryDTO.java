package com.usn.labhub.user.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "考勤分页查询参数")
public class AttendanceQueryDTO {

    @Schema(description = "页码，从 1 开始")
    @Min(value = 1, message = "页码必须大于 0")
    private Long pageNo = 1L;

    @Schema(description = "每页条数")
    @Min(value = 1, message = "每页条数必须大于 0")
    private Long pageSize = 10L;

    @Schema(description = "真实姓名，模糊查询")
    private String username;

    @Schema(description = "工作组 ID")
    private Integer groupId;

    @Schema(description = "考勤状态：CHECKED_IN 已签到，CHECKED_OUT 已签退，IN_PROGRESS 进行中")
    private String attendanceStatus;

    @Schema(description = "签到开始日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Schema(description = "签到结束日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
