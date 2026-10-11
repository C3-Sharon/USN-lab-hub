package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

@Data
public class LearningStageAccessRecord {
    private Long id;
    private Long roadmapId;
    private String roadmapStatus;
}
