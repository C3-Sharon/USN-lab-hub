package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CollectionPlanCreateDTO {
    @NotBlank
    @Size(min = 2, max = 120)
    private String name;
    @NotBlank
    @Size(max = 1000)
    private String purpose;
    @NotNull
    @Positive
    private Long ownerId;
    @NotBlank
    @Pattern(regexp = "FILE|MANUAL")
    private String sourceType;
    @NotEmpty
    @Size(max = 100)
    private List<@Valid MetricDefinitionDTO> metricDefinitions;
    @Positive
    private Long linkedExperimentRecordId;
    @Size(max = 500)
    private String retentionNote;
}
