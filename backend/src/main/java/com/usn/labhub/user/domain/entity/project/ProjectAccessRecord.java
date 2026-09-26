package com.usn.labhub.user.domain.entity.project;

import lombok.Data;

@Data
public class ProjectAccessRecord {
    private Long id;
    private String status;
    private String myRole;
}
