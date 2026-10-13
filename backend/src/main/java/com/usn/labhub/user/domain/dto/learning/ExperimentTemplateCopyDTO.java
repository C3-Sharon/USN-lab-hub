package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentTemplateCopyDTO {
    @NotNull
    @Positive
    private Long targetProjectId;

    @Size(max = 120)
    private String title;

    @Size(max = 500)
    private String description;
}
