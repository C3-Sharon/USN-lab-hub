package com.usn.labhub.user.domain.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record InventoryReturnCreateDTO(
        @NotBlank String reason,
        @NotEmpty List<@Valid Line> lines) {

    public record Line(
            @NotNull Long fulfillmentLineId,
            @NotNull BigDecimal quantity) {
    }
}
