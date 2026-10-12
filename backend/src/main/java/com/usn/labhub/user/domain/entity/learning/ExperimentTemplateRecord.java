package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentTemplateRecord {
    private Long id;
    private String title;
    private String description;
    private String scope;
    private Long projectId;
    private String category;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
