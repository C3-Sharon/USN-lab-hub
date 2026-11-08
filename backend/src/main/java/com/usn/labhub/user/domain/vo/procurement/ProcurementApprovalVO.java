package com.usn.labhub.user.domain.vo.procurement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcurementApprovalVO {
    private Long id;
    private String decision;
    private Integer policyVersion;
    private String actorType;
    private Long decidedBy;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String comment;
}
