package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcurementReceiptRecord {
    private Long id;
    private String receiptNo;
    private Long orderId;
    private String status;
    private LocalDateTime receivedAt;
    private Long receiverId;
    private String receiverName;
    private String note;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
