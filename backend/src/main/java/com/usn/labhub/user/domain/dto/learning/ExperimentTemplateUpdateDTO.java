package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentTemplateUpdateDTO {
    @NotBlank
    @Size(min = 2, max = 120)
    private String title;

    @Size(max = 500)
    private String description;

    @Size(max = 50)
    private String category;
}
