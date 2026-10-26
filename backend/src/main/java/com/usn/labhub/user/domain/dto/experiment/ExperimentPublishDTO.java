package com.usn.labhub.user.domain.dto.experiment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExperimentPublishDTO {
    @NotNull @Min(1)
    private Integer recordVersion;
    @NotNull @Min(1)
    private Long revisionId;
}
