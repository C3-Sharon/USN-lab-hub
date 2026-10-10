package com.usn.labhub.user.domain.vo.learning;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LearningUnitCompletionVO {
    private Long unitId;
    private Boolean completed;
    private LearningProgressVO roadmapProgress;
}
