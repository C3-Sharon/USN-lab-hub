package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StatusChangeDTO {
    @NotNull
    @Min(1)
    private Integer version;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
