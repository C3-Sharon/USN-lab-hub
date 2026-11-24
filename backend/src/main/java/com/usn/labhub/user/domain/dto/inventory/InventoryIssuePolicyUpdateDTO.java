package com.usn.labhub.user.domain.dto.inventory;

import java.math.BigDecimal;
import java.util.List;

public record InventoryIssuePolicyUpdateDTO(
        Integer basePolicyVersion,
        BigDecimal directAmountThreshold,
        BigDecimal valuableUnitCostThreshold,
        BigDecimal defaultDirectQuantityLimit,
        BigDecimal lowStockRatio,
        Integer reservationTtlHours,
        List<Long> forcedCategoryIds,
        List<InventoryIssuePolicyOverrideDTO> componentOverrides) {
}
