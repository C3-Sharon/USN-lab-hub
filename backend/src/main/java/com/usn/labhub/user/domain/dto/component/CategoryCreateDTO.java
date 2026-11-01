package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryCreateDTO {
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]{2,40}$")
    private String code;

    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    private Long parentId;

    @Min(0)
    @Max(9999)
    private Integer sortOrder = 0;
}
