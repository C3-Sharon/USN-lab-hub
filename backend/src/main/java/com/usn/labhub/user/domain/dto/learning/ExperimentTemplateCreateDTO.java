package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentTemplateCreateDTO {
    @NotBlank
    @Size(min = 2, max = 120)
    private String title;

    @Size(max = 500)
    private String description;

    @NotBlank
    private String scope;

    private Long projectId;

    @Size(max = 50)
    private String category;
}
