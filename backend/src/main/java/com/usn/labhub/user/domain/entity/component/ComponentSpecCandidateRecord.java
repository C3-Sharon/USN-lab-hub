package com.usn.labhub.user.domain.entity.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComponentSpecCandidateRecord {
    private Long id;
    private Long componentId;
    private String status;
    private String sourceType;
    private String sourceUrl;
    private String sourceTitle;
    private LocalDateTime capturedAt;
    private String rawSpecsJson;
    private String normalizedProposalJson;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private String decisionNote;
    private Integer version;
}
