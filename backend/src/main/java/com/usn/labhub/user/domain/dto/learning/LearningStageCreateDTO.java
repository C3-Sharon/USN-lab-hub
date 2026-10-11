package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearningStageCreateDTO {
    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    @Size(max = 500)
    private String description;

    @Min(0)
    private Integer sortOrder = 0;
}
