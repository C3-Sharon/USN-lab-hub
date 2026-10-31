package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MediaOrderItemDTO(
        @NotNull @Min(1) Long mediaId,
        @NotNull @Min(1) Integer mediaVersion,
        @NotNull @Min(0) @Max(9999) Integer sortOrder) {
}
