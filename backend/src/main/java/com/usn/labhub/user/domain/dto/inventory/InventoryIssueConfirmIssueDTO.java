package com.usn.labhub.user.domain.dto.inventory;

import jakarta.validation.constraints.NotNull;

public record InventoryIssueConfirmIssueDTO(
        @NotNull Integer requestVersion,
        @NotNull Integer reservationVersion,
        String note) {
}
