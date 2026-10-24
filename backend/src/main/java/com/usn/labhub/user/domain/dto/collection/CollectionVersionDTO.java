package com.usn.labhub.user.domain.dto.collection;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CollectionVersionDTO {
    @NotNull
    @Positive
    private Integer version;
    @Size(max = 500)
    private String completionReason;
}
