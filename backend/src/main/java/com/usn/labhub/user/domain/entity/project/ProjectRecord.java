package com.usn.labhub.user.domain.entity.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectRecord {
    private Long id;
    private String projectCode;
    private String projectName;
    private String description;
    private Long ownerId;
    private String status;
    private Boolean publicVisible;
    private String category;
    private Long coverMediaId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
