package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningRoadmapCreateVO {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String difficulty;
    private Integer estimatedHours;
    private String coverMediaId;
    private Integer sortOrder;
    private Integer stageCount;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
