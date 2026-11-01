package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComponentCreateDTO {
    @NotBlank
    @Size(min = 2, max = 120)
    private String name;

    @NotNull
    private Long manufacturerId;

    @NotBlank
    @Size(max = 120)
    private String mpn;

    @NotNull
    private Long categoryId;

    @NotBlank
    @Size(max = 80)
    private String packageType;

    @NotBlank
    @Pattern(regexp = "^(PCS|METER|GRAM|MILLILITER|SET)$")
    private String baseUnit;

    @Size(max = 2000)
    private String description;
}
