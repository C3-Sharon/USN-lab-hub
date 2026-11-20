package com.usn.labhub.user.domain.vo.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryMovementVO {
    private Long id;
    private String movementNo;
    private String movementType;
    private Long componentId;
    private Long batchId;
    private String batchNo;
    private String sourceType;
    private Long sourceId;
    private BigDecimal quantityDelta;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String unit;
    private BigDecimal unitCost;
    private String reason;
    private Long operatorId;
    private LocalDateTime occurredAt;
}
