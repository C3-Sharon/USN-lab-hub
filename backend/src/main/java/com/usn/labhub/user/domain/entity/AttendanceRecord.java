package com.usn.labhub.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 考勤记录表
 * </p>
 *
 * @author 陈思瑞
 * @since 2026-04-16
 */
@Getter
@Setter
@TableName("attendance_record")
public class AttendanceRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("check_in_time")
    private LocalDateTime checkInTime;

    @TableField("check_in_date")
    private LocalDate checkInDate;

    @TableField("check_out_time")
    private LocalDateTime checkOutTime;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("semester")
    private String semester;

    @TableField("source")
    private String source;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
