package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningRecord {
    private Long id;
    private Long roadmapId;
    private Long userId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
