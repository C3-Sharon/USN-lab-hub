package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CollectionSnapshotCreateDTO {
    @NotNull @Min(1) private Integer batchVersion;
    @NotBlank private String snapshotType;
}
