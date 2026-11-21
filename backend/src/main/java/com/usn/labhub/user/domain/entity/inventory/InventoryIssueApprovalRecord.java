package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryIssueApprovalRecord {
    private Long id;
    private Long requestId;
    private String decision;
    private String actorType;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private Integer policyVersion;
    private String triggerSnapshotJson;
    private String comment;
    private String requestIdempotencyId;
}
