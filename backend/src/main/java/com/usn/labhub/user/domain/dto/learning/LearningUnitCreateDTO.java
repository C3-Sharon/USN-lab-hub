package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearningUnitCreateDTO {
    @NotBlank
    @Size(min = 2, max = 120)
    private String title;

    @Size(max = 2000)
    private String description;

    @Min(0)
    private Integer sortOrder = 0;

    private Long templateId;
}
