package com.usn.labhub.user.domain.dto.component;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class SpecCandidateCreateDTO {
    @NotNull
    @Min(1)
    private Integer componentVersion;

    @NotBlank
    @Pattern(regexp = "^(MANUFACTURER_WEBSITE|MANUFACTURER_DATASHEET|AUTHORIZED_DISTRIBUTOR|OTHER_PUBLIC_URL|MANUAL)$")
    private String sourceType;

    @Size(max = 2048)
    private String sourceUrl;

    @Size(max = 200)
    private String sourceTitle;

    private OffsetDateTime capturedAt;

    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid RawSpecificationDTO> rawSpecs;

    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid SpecificationDTO> normalizedProposal;
}
