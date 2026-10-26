package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollectionRecordRow {
    private Long id;
    private Long batchId;
    private Long dataImportRevisionId;
    private Long manualRecordRevisionId;
    private String sourceType;
    private String metricCode;
    private String valueType;
    private String valueText;
    private String unit;
    private LocalDateTime observedAt;
    private Long recordedBy;
    private String method;
    private String instrument;
    private Long rawRowNo;
    private String qualityStatus;
    private String parserVersion;
    private LocalDateTime createTime;
}
