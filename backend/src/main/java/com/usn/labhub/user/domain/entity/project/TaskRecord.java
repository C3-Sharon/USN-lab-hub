package com.usn.labhub.user.domain.entity.project;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskRecord {
    private Long id;
    private Long projectId;
    private Long milestoneId;
    private String title;
    private String description;
    private String status;
    private Long assigneeUserId;
    private String priority;
    private LocalDate dueDate;
    private String blockReason;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
