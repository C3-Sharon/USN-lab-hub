package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryUpdateDTO {
    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    @NotNull
    @Min(0)
    @Max(9999)
    private Integer sortOrder;

    @NotNull
    @Min(1)
    private Integer version;
}
