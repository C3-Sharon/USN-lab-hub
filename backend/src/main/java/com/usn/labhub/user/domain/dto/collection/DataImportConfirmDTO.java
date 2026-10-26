package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DataImportConfirmDTO {
    @NotNull @Min(1) private Integer version;
    @NotNull @Min(1) private Integer importVersion;
    @NotNull @Min(1) private Integer batchVersion;
}
