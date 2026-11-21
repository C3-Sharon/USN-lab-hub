package com.usn.labhub.user.domain.dto.inventory;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryWriteOffDTO(
        @NotNull BigDecimal quantity,
        String reason) {
}
