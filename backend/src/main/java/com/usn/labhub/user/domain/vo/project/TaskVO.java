package com.usn.labhub.user.domain.vo.project;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskVO {
    private Long id;
    private Long projectId;
    private Long milestoneId;
    private String milestoneName;
    private String title;
    private String description;
    private String status;
    private Long assigneeUserId;
    private String assigneeName;
    private String priority;
    private LocalDate dueDate;
    private String blockReason;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
