package com.usn.labhub.user.domain.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectCreateDTO {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9-]{3,32}$")
    private String code;

    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    @Size(max = 500)
    private String summary;

    @Pattern(regexp = "^$|^[a-z][a-z0-9_]{1,31}$")
    private String category;

    private Long coverMediaId;
}
