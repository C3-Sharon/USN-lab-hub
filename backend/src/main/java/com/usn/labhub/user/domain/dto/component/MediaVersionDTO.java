package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MediaVersionDTO(
        @NotNull @Min(1) Integer componentVersion,
        @NotNull @Min(1) Integer mediaVersion) {
}
