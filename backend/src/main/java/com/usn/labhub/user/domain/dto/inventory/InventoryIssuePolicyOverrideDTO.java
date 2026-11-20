package com.usn.labhub.user.domain.dto.inventory;

import java.math.BigDecimal;

public record InventoryIssuePolicyOverrideDTO(
        Long componentId,
        BigDecimal directQuantityLimit) {
}
