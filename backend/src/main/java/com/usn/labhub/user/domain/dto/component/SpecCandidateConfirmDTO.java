package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SpecCandidateConfirmDTO {
    @NotNull
    @Min(1)
    private Integer componentVersion;

    @NotNull
    @Min(1)
    private Integer candidateVersion;

    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid SpecificationDTO> specifications;

    @NotBlank
    @Size(max = 500)
    private String changeSummary;
}
