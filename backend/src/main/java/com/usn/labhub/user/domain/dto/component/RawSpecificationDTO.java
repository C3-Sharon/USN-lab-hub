package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RawSpecificationDTO {
    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String value;

    @Size(max = 20)
    private String unit;
}
