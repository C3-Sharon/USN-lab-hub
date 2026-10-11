package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningWorkbenchItemVO {
    private Long roadmapId;
    private String roadmapTitle;
    private String roadmapDifficulty;
    private String status;
    private Integer progress;
    private Integer completedUnitCount;
    private Integer totalUnitCount;
    private LocalDateTime updateTime;
}
