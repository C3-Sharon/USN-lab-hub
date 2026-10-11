package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearningRoadmapCreateDTO {

    @NotBlank
    @Size(min = 2, max = 80)
    private String title;

    @Size(max = 500)
    private String description;

    @Pattern(regexp = "^DRAFT$")
    private String status = "DRAFT";

    @Pattern(regexp = "^(BEGINNER|INTERMEDIATE|ADVANCED)$")
    private String difficulty = "BEGINNER";

    @Min(1)
    private Integer estimatedHours;

    @Size(max = 64)
    private String coverMediaId;

    @Min(0)
    private Integer sortOrder = 0;
}
