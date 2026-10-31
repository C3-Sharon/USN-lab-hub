package com.usn.labhub.user.domain.vo.component;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SpecCandidateVO {
    private Long id;
    private Long componentId;
    private String status;
    private String sourceType;
    private String sourceUrl;
    private String sourceTitle;
    private LocalDateTime capturedAt;
    private List<Map<String, Object>> rawSpecs;
    private List<Map<String, Object>> normalizedProposal;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private String decisionNote;
    private Integer version;
}
