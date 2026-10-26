package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MetricDefinitionDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,63}")
    private String code;
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotBlank
    @Pattern(regexp = "DECIMAL|INTEGER|BOOLEAN|STRING")
    private String valueType;
    @NotNull
    @Size(max = 50)
    private String unit;
    @NotNull
    private Boolean required;
}
