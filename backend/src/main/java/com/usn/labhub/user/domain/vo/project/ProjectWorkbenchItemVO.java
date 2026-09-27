package com.usn.labhub.user.domain.vo.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectWorkbenchItemVO {
    private Long id;
    private String code;
    private String name;
    private String status;
    private String myRole;
    private String coverUrl;
    private LocalDateTime updateTime;
}
