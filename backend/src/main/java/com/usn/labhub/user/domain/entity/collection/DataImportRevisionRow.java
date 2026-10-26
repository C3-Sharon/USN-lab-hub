package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DataImportRevisionRow {
    private Long id;
    private Long dataImportId;
    private Integer importRevisionNo;
    private String mappingJson;
    private String mappingDigest;
    private String status;
    private Long recordCount;
    private Long rejectedCount;
    private Long previousRevisionId;
    private Long createdBy;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createTime;
}
