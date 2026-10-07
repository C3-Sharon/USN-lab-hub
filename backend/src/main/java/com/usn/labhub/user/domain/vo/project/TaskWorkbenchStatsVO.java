package com.usn.labhub.user.domain.vo.project;

import lombok.Data;

@Data
public class TaskWorkbenchStatsVO {
    private Integer todo;
    private Integer inProgress;
    private Integer blocked;
    private Integer doneThisWeek;
}
