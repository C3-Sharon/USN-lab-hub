package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ManualRecordRevisionRow {
    private Long id;
    private Long manualRecordId;
    private Integer revisionNo;
    private String valueType;
    private String valueText;
    private String unit;
    private LocalDateTime observedAt;
    private String method;
    private String instrument;
    private String note;
    private String changeReason;
    private Long previousRevisionId;
    private Long createdBy;
    private LocalDateTime createTime;
}
