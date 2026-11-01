package com.usn.labhub.user.domain.vo.component;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SpecSnapshotVO {
    private Long id;
    private Long componentId;
    private Integer snapshotVersion;
    private String sourceType;
    private String sourceUrl;
    private String sourceTitle;
    private LocalDateTime capturedAt;
    private Long candidateId;
    private String sourceNote;
    private List<Map<String, Object>> rawSpecs;
    private List<Map<String, Object>> specifications;
    private String changeSummary;
    private String checksum;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
}
