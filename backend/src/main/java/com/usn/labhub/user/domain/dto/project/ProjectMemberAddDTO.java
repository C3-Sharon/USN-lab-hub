package com.usn.labhub.user.domain.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectMemberAddDTO {

    @NotBlank
    @Size(max = 64)
    private String memberId;

    @NotBlank
    @Pattern(regexp = "^(OWNER|MAINTAINER|MEMBER|OBSERVER)$")
    private String projectRole;
}
