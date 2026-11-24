package com.usn.labhub.user.domain.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record InventoryIssueReallocateDTO(
        @NotNull Integer requestVersion,
        @NotBlank String reason,
        @NotEmpty List<@Valid Allocation> allocations) {

    public record Allocation(
            @NotNull Long reservationId,
            @NotNull Long batchId,
            @NotNull BigDecimal quantity) {
    }
}
