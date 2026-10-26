package com.usn.labhub.user.domain.dto.collection;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ManualRecordCreateDTO {
    @NotBlank @Size(max = 64) private String metricCode;
    @NotBlank private String valueType;
    @NotNull private JsonNode value;
    @NotBlank @Size(max = 50) private String unit;
    @NotNull private OffsetDateTime observedAt;
    @NotBlank @Size(max = 500) private String method;
    @NotBlank @Size(max = 500) private String instrument;
    @Size(max = 1000) private String note;
}
