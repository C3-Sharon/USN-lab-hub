package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningEnrollmentVO {
    private Long id;
    private Long roadmapId;
    private Long userId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer progress;
    private Integer completedUnitCount;
    private Integer totalUnitCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
