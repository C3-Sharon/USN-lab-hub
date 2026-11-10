package com.usn.labhub.user.domain.vo.procurement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcurementAttachmentVO {
    private Long id;
    private String ownerType;
    private Long ownerId;
    private Long mediaId;
    private String purpose;
    private String invoiceNo;
    private String note;
    private Integer attachmentVersion;
    private String originalFileName;
    private String contentType;
    private Long size;
    private Integer width;
    private Integer height;
    private String sha256;
    private String contentPath;
    private Integer mediaVersion;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}
