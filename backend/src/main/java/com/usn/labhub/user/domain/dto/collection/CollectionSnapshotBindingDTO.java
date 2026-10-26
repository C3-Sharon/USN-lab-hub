package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CollectionSnapshotBindingDTO {
    @NotNull @Min(1) private Integer recordVersion;
    @NotNull @Min(1) private Long revisionId;
    @NotNull @Min(1) private Integer revisionVersion;
    @Min(1) private Long snapshotId;
    private Boolean replaceExisting = false;
}
