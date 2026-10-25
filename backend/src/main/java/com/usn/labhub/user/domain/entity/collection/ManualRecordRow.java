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
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
