package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ManualSpecSnapshotDTO {
    @NotNull
    @Min(1)
    private Integer componentVersion;

    @NotBlank
    @Pattern(regexp = "^MANUAL$")
    private String sourceType;

    @Size(max = 2048)
    private String sourceUrl;

    @NotBlank
    @Size(max = 1000)
    private String sourceNote;

    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid RawSpecificationDTO> rawSpecs;

    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid SpecificationDTO> specifications;

    @NotBlank
    @Size(max = 500)
    private String changeSummary;
}
