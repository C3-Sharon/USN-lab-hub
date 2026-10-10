package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

@Data
public class LearningUnitAccessRecord {
    private Long id;
    private Long stageId;
    private Long roadmapId;
    private String roadmapStatus;
}
