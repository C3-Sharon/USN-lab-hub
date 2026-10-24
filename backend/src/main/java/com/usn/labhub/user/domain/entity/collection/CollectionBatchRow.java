package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionBatchRow {
    private Long id;
    private String batchCode;
    private Long planId;
    private String planName;
    private Long planOwnerId;
    private Long projectId;
    private String projectName;
    private String projectStatus;
    private String actorProjectRole;
    private String sourceType;
    private String status;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long recordCount;
    private Long rejectedCount;
    private LocalDateTime observedFrom;
    private LocalDateTime observedTo;
    private Long sourceRevisionId;
    private String completionReason;
    private String checksum;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
