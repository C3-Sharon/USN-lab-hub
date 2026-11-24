package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryWriteOffCommandRecord {
    private Long id;
    private String commandNo;
    private String type;
    private Long componentId;
    private Long batchId;
    private BigDecimal quantity;
    private String reason;
    private Long operatorId;
    private LocalDateTime occurredAt;
    private Long movementId;
    private String requestId;
    private Long createdBy;
    private LocalDateTime createTime;
}
