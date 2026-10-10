package com.usn.labhub.user.domain.vo.project;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MilestoneVO {
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer sortOrder;
    private Long taskCount;
    private Long taskDone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
