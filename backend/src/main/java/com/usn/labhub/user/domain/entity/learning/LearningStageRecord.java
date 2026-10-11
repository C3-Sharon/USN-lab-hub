package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningStageRecord {
    private Long id;
    private Long roadmapId;
    private String name;
    private String description;
    private Boolean descriptionSpecified;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
