package com.usn.labhub.user.domain.vo.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectSummaryVO {
    private Long id;
    private String code;
    private String name;
    private String summary;
    private String category;
    private String status;
    private String coverUrl;
    private String myRole;
    private Integer memberCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
