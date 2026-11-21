package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryIssueFulfillmentRecord {
    private Long id;
    private String fulfillmentNo;
    private Long requestId;
    private Long operatorId;
    private String operatorName;
    private Long recipientId;
    private String recipientName;
    private LocalDateTime issuedAt;
    private String note;
    private String requestIdempotencyId;
}
