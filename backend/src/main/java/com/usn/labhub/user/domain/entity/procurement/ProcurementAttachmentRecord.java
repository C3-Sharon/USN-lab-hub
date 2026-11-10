package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcurementAttachmentRecord {
    private Long id;
    private String ownerType;
    private Long ownerId;
    private Long mediaId;
    private String purpose;
    private String invoiceNo;
    private String note;
    private Integer version;
    private String status;
    private Long createdBy;
    private LocalDateTime createTime;
    private Long deletedBy;
    private LocalDateTime deletedAt;
}
