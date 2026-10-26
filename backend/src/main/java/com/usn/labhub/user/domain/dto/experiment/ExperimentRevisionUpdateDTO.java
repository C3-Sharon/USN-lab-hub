package com.usn.labhub.user.domain.dto.experiment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentRevisionUpdateDTO {
    @NotNull @Min(1)
    private Integer recordVersion;
    @NotNull @Min(1)
    private Integer revisionVersion;
    @NotNull
    private JsonNode contentJson;
    @Size(max = 1000)
    private String purpose;
    @Size(max = 1000)
    private String environment;
    @Size(max = 10000)
    private String steps;
    @Size(max = 5000)
    private String result;
    @Size(max = 5000)
    private String anomaly;
    @Size(max = 5000)
    private String conclusion;
    @Size(max = 100)
    private String hardwareVersionRef;
    @Size(max = 100)
    private String deviceInstanceRef;
    @Size(max = 100)
    private String firmwareVersion;
    @Size(max = 100)
    private String bomVersionRef;
    @Size(max = 500)
    private String changeSummary;
}
