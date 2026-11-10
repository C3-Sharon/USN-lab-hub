package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryMovementRecord {
    private Long id;
    private String movementNo;
    private Long componentId;
    private Long batchId;
    private Long sourceId;
    private BigDecimal quantityDelta;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String unit;
    private BigDecimal unitCost;
    private Long operatorId;
    private LocalDateTime occurredAt;
    private String requestId;
}
