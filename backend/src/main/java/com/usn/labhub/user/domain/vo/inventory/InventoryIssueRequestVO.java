package com.usn.labhub.user.domain.vo.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InventoryIssueRequestVO {
    private Long id;
    private String requestNo;
    private String title;
    private String purpose;
    private Long requesterId;
    private String requesterName;
    private Long projectId;
    private String projectName;
    private String status;
    private Boolean approvalRequired;
    private List<String> approvalReasons;
    @JsonIgnore
    private String approvalReasonsJson;
    private Integer policyVersion;
    private BigDecimal estimatedAmount;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private Long updatedBy;
    private LocalDateTime updateTime;
    private List<InventoryIssueLineVO> lines;
}
