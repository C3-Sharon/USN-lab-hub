package com.usn.labhub.user.domain.entity.component;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComponentSpecSnapshotRecord {
    private Long id;
    private Long componentId;
    private Integer snapshotVersion;
    private String sourceType;
    private String sourceUrl;
    private String sourceTitle;
    private LocalDateTime capturedAt;
    private Long candidateId;
    private String sourceNote;
    private String rawSpecsJson;
    private String specificationsJson;
    private String changeSummary;
    private String checksum;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
}
