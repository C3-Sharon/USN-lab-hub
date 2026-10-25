package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;

@Data
public class ExperimentSnapshotBindingRow {
    private Long recordId;
    private Long authorId;
    private Integer recordVersion;
    private Long projectId;
    private Long revisionId;
    private String revisionStatus;
    private Integer revisionVersion;
    private Long collectionSnapshotId;
}
