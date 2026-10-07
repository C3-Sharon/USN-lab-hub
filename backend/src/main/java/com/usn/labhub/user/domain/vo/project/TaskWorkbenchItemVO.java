package com.usn.labhub.user.domain.vo.project;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskWorkbenchItemVO {
    private Long id;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String milestoneName;
    private String title;
    private String status;
    private String priority;
    private LocalDate dueDate;
    private LocalDateTime updateTime;
}
