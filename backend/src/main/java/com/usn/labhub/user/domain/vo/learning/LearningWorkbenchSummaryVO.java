package com.usn.labhub.user.domain.vo.learning;

import java.util.List;

public record LearningWorkbenchSummaryVO(
        LearningWorkbenchStatsVO stats,
        List<LearningWorkbenchItemVO> list) {
}
