package com.usn.labhub.user.domain.dto.inventory;

import java.math.BigDecimal;

public record InventoryIssueRequestLineDTO(
        Long componentId,
        BigDecimal requestedQuantity) {
}
