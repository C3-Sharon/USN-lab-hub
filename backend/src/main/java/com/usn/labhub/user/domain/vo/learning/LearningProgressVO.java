package com.usn.labhub.user.domain.vo.learning;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LearningProgressVO {
    private Long roadmapId;
    private String status;
    private Integer progress;
    private Integer completedUnitCount;
    private Integer totalUnitCount;
}
