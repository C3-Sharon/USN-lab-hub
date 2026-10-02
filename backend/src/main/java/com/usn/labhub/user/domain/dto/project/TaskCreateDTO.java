package com.usn.labhub.user.domain.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskCreateDTO {
    @NotBlank @Size(min = 2, max = 120)
    private String title;
    @Size(max = 2000)
    private String description;
    private Long milestoneId;
    private Long assigneeUserId;
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$")
    private String priority = "MEDIUM";
    private LocalDate dueDate;
}
