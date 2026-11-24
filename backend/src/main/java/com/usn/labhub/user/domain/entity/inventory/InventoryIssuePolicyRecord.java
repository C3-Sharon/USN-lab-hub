package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryIssuePolicyRecord {
    private Long id;
    private Integer policyVersion;
    private String currency;
    private BigDecimal directAmountThreshold;
    private BigDecimal valuableUnitCostThreshold;
    private BigDecimal defaultDirectQuantityLimit;
    private BigDecimal lowStockRatio;
    private Integer reservationTtlHours;
    private LocalDateTime effectiveAt;
    private Long createdBy;
    private LocalDateTime createTime;
}
