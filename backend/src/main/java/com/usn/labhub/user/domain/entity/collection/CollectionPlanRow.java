package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionPlanRow {
    private Long id;
    private String planCode;
    private Long projectId;
    private String projectName;
    private String projectStatus;
    private String actorProjectRole;
    private String name;
    private String purpose;
    private Long ownerId;
    private String ownerName;
    private String sourceType;
    private String metricDefinitionsJson;
    private Long linkedExperimentRecordId;
    private String linkedExperimentRecordCode;
    private String retentionNote;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long batchCount;
    private Long latestBatchId;
    private String latestBatchCode;
    private String latestBatchStatus;
    private LocalDateTime latestBatchCreateTime;
}
