package com.usn.labhub.user.domain.entity.iot;

import lombok.Data;

@Data
public class LabProjectRecord {
    private Long id;
    private String projectCode;
    private String projectName;
    private String description;
    private String status;
    private Boolean publicVisible;
}
