package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MediaDeleteDTO(
        @NotNull @Min(1) Integer componentVersion,
        @NotNull @Min(1) Integer mediaVersion,
        @NotBlank @Size(max = 500) String reason) {
}
