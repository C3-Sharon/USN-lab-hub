package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollectionSnapshotRow {
    private Long id;
    private String snapshotCode;
    private Long batchId;
    private String snapshotType;
    private Long sourceRevisionId;
    private Long recordCount;
    private Long rejectedCount;
    private LocalDateTime observedFrom;
    private LocalDateTime observedTo;
    private String batchChecksum;
    private String mappingDigest;
    private Long createdBy;
    private LocalDateTime createTime;
}
