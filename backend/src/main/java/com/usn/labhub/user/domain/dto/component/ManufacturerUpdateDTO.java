package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManufacturerUpdateDTO {
    @NotBlank
    @Size(min = 2, max = 120)
    private String name;

    @Size(max = 2048)
    @Pattern(regexp = "^https?://.+$")
    private String website;

    @NotNull
    @Min(1)
    private Integer version;
}
