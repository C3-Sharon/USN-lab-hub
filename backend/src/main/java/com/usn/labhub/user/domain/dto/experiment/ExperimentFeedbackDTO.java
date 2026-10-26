package com.usn.labhub.user.domain.dto.experiment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentFeedbackDTO {
    @NotNull @Min(1)
    private Integer recordVersion;
    @NotNull @Min(1)
    private Long revisionId;
    @NotBlank
    @Pattern(regexp = "^(COMMENT|CHANGE_REQUEST|APPROVAL)$")
    private String type;
    @Size(max = 2000)
    private String content;
    @NotBlank
    @Pattern(regexp = "^(AUTHOR_AND_REVIEWERS|PROJECT_MEMBERS)$")
    private String visibility = "AUTHOR_AND_REVIEWERS";
}
