package com.usn.labhub.user.domain.vo.project;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MilestoneStatusVO {
    private Long id;
    private String status;
    private LocalDateTime updateTime;
}
