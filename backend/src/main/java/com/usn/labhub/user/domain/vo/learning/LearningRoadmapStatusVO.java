package com.usn.labhub.user.domain.vo.learning;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LearningRoadmapStatusVO {
    private Long id;
    private String status;
    private LocalDateTime updateTime;
}
