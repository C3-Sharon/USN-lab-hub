package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningStageSummaryVO {
    private Long id;
    private Long roadmapId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer unitCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
