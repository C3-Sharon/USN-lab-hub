package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MediaOrderDTO(
        @NotNull @Min(1) Integer componentVersion,
        @NotEmpty @Size(max = 5) List<@Valid MediaOrderItemDTO> items) {
}
