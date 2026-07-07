package com.usn.labhub.user.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "考勤导出记录")
public class AttendanceExportVO {

    @ExcelProperty("姓名")
    @Schema(description = "姓名")
    private String username;

    @ExcelProperty("学号")
    @Schema(description = "学号/工号")
    private String memberId;

    @ExcelProperty("组别")
    @Schema(description = "组别")
    private String groupName;

    @ExcelProperty("签到时间")
    @Schema(description = "签到时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkInTime;

    @ExcelProperty("签退时间")
    @Schema(description = "签退时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkOutTime;

    @ExcelProperty("本次时长(分钟)")
    @Schema(description = "本次时长，分钟")
    private Integer durationMinutes;

    @ExcelProperty("所属学期")
    @Schema(description = "所属学期")
    private String semester;

    @ExcelProperty("操作来源")
    @Schema(description = "操作来源")
    private String source;
}
