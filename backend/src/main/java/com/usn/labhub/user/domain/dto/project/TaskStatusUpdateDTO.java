package com.usn.labhub.user.domain.dto.project;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TaskStatusUpdateDTO {
    @NotBlank @Pattern(regexp = "^(TODO|IN_PROGRESS|BLOCKED|DONE|CANCELED)$")
    private String status;
    @NotNull @Min(1)
    private Integer version;
    @Size(max = 500)
    private String blockReason;
}
