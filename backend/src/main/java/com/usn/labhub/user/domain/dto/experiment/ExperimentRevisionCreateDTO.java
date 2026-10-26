package com.usn.labhub.user.domain.dto.experiment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentRevisionCreateDTO {
    @NotNull @Min(1)
    private Integer recordVersion;
    @NotBlank @Size(max = 500)
    private String changeSummary;
}
