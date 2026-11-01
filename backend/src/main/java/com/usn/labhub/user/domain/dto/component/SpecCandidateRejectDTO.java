package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpecCandidateRejectDTO {
    @NotNull
    @Min(1)
    private Integer candidateVersion;

    @Size(max = 1000)
    private String decisionNote;
}
