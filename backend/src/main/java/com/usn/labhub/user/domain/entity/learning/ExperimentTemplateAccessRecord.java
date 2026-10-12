package com.usn.labhub.user.domain.entity.learning;

import lombok.Data;

@Data
public class ExperimentTemplateAccessRecord {
    private Long id;
    private String scope;
    private Long projectId;
    private String projectRole;
    private boolean hasPublished;
}
