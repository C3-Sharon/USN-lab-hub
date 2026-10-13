package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentTemplateVersionDeprecateDTO {
    @NotBlank
    @Size(max = 500)
    private String reason;
}
