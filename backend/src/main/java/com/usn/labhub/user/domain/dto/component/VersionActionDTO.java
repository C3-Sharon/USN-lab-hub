package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VersionActionDTO {
    @NotNull
    @Min(1)
    private Integer version;
}
