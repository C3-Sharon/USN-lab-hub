package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ManualRecordRow {
    private Long id;
    private Long batchId;
    private Long projectId;
    private String metricCode;
    private Long currentRevisionId;
    private Integer version;
    private Integer revisionNo;
    private String valueType;
    private String valueText;
    private String unit;
    private LocalDateTime observedAt;
    private String method;
    private String instrument;
    private String note;
    private Long previousRevisionId;
    private Long recordedBy;
    private String recordedByName;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
