package com.usn.labhub.user.domain.dto.inventory;

import jakarta.validation.constraints.NotNull;

public record InventoryReturnCommandDTO(
        @NotNull Integer returnVersion,
        String comment) {
}
