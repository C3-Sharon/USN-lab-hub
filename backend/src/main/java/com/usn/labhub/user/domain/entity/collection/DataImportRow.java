package com.usn.labhub.user.domain.entity.collection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DataImportRow {
    private Long id;
    private Long batchId;
    private Long projectId;
    private String objectKey;
    private String originalFileName;
    private String mimeType;
    private Long fileSize;
    private String fileSha256;
    private String status;
    private Long currentRevisionId;
    private Integer version;
    private Long uploadedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
