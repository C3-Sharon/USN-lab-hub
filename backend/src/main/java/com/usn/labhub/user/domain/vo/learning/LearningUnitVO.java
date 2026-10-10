package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningUnitVO {
    private Long id;
    private Long stageId;
    private String title;
    private String description;
    private Integer sortOrder;
    private Long templateId;
    private String templateName;
    private Boolean completed;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
