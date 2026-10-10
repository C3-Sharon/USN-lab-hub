package com.usn.labhub.user.domain.dto.project;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MilestoneCreateDTO {

    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    @Size(max = 500)
    private String description;

    @NotBlank
    @Pattern(regexp = "^(PLANNED|IN_PROGRESS|COMPLETED)$")
    private String status;

    private LocalDate startDate;
    private LocalDate endDate;

    @NotNull
    @Min(0)
    private Integer sortOrder = 0;
}
