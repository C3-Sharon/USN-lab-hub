package com.usn.labhub.user.domain.dto.learning;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentTemplateVersionUpdateDTO {
    @NotNull
    private JsonNode schemaJson;

    @Size(max = 500)
    private String changelog;
}
