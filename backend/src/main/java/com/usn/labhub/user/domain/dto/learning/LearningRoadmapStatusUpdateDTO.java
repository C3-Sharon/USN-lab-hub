package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LearningRoadmapStatusUpdateDTO {

    @NotBlank
    @Pattern(regexp = "^(DRAFT|PUBLISHED|ARCHIVED)$")
    private String status;
}
