package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningRoadmapRecord {
    private Long id;
    private String title;
    private String description;
    private Boolean descriptionSpecified;
    private String status;
    private String difficulty;
    private Integer estimatedHours;
    private String coverMediaId;
    private Boolean coverMediaIdSpecified;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
