package com.usn.labhub.user.domain.dto.experiment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExperimentSubmitDTO {
    @NotNull @Min(1)
    private Integer recordVersion;
    @NotNull @Min(1)
    private Long revisionId;
    @NotNull @Min(1)
    private Integer revisionVersion;
}
