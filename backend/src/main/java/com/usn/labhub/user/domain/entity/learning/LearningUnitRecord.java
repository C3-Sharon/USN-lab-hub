package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningUnitRecord {
    private Long id;
    private Long stageId;
    private String title;
    private String description;
    private Boolean descriptionSpecified;
    private Integer sortOrder;
    private Long templateId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
