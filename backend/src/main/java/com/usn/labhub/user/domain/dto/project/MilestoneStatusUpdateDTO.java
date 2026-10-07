package com.usn.labhub.user.domain.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MilestoneStatusUpdateDTO {

    @NotBlank
    @Pattern(regexp = "^(PLANNED|IN_PROGRESS|COMPLETED)$")
    private String status;
}
