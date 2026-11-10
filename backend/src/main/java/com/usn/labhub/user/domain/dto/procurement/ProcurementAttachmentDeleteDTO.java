package com.usn.labhub.user.domain.dto.procurement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProcurementAttachmentDeleteDTO(
        @NotNull @Min(1) Integer attachmentVersion,
        @NotNull @Min(1) Integer mediaVersion,
        @NotNull @Min(1) Integer ownerVersion,
        @NotBlank @Size(max = 500) String reason) {
}
