package com.usn.labhub.user.domain.vo.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InventoryIssuePolicyVO {
    private Long id;
    private Integer policyVersion;
    private String currency;
    private BigDecimal directAmountThreshold;
    private BigDecimal valuableUnitCostThreshold;
    private BigDecimal defaultDirectQuantityLimit;
    private BigDecimal lowStockRatio;
    private Integer reservationTtlHours;
    private List<Long> forcedCategoryIds;
    private List<InventoryIssuePolicyOverrideVO> componentOverrides;
    private LocalDateTime effectiveAt;
    private Long createdBy;
    private LocalDateTime createTime;
}
