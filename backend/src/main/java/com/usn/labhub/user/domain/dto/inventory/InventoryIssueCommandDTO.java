package com.usn.labhub.user.domain.dto.inventory;

import jakarta.validation.constraints.NotNull;

public record InventoryIssueCommandDTO(
        @NotNull Integer requestVersion,
        String comment) {
}
