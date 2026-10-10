package com.usn.labhub.user.domain.vo.learning;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LearningStageVO {
    private Long id;
    private Long roadmapId;
    private String name;
    private String description;
    private Integer sortOrder;
    private List<LearningUnitVO> units;
    private Integer unitCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
