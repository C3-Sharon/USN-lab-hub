package com.usn.labhub.user.domain.vo.project;

import java.util.List;

public record TaskWorkbenchSummaryVO(TaskWorkbenchStatsVO stats, List<TaskWorkbenchItemVO> list) {
}
