package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpecificationDTO {
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}$")
    private String specCode;

    @NotBlank
    @Size(min = 2, max = 80)
    private String label;

    @NotBlank
    @Pattern(regexp = "^(STRING|DECIMAL|INTEGER|BOOLEAN)$")
    private String valueType;

    @Size(max = 500)
    private String rawValue;

    @NotNull
    private Object normalizedValue;

    @Size(max = 20)
    private String unit;

    @NotNull
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
