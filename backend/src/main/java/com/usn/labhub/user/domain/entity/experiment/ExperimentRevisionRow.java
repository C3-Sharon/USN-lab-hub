package com.usn.labhub.user.domain.entity.experiment;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperimentRevisionRow {
    private Long id;
    private Long recordId;
    private Integer revisionNo;
    private String status;
    private String contentJson;
    private String purpose;
    private String environment;
    private String steps;
    private String result;
    private String anomaly;
    private String conclusion;
    private String hardwareVersionRef;
    private String deviceInstanceRef;
    private String firmwareVersion;
    private String bomVersionRef;
    private Long collectionSnapshotId;
    private String changeSummary;
    private Long previousRevisionId;
    private Long createdBy;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
