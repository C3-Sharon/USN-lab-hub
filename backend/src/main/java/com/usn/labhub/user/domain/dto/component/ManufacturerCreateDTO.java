package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManufacturerCreateDTO {
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]{2,40}$")
    private String code;

    @NotBlank
    @Size(min = 2, max = 120)
    private String name;

    @Size(max = 2048)
    @Pattern(regexp = "^https?://.+$")
    private String website;
}
