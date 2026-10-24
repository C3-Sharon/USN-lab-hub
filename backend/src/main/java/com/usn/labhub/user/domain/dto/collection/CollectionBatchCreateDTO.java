package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CollectionBatchCreateDTO {
    @NotNull
    @Positive
    private Integer planVersion;
    @NotBlank
    @Pattern(regexp = "FILE|MANUAL")
    private String sourceType;
}
