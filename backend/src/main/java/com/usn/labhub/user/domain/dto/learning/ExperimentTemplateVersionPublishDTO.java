package com.usn.labhub.user.domain.dto.learning;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentTemplateVersionPublishDTO {
    @Size(max = 500)
    private String changelog;
}
