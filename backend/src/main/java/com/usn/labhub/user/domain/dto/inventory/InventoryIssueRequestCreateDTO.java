package com.usn.labhub.user.domain.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InventoryIssueRequestCreateDTO(
        @NotBlank @Size(min = 2, max = 120) String title,
        @NotBlank @Size(min = 2, max = 500) String purpose,
        Long projectId,
        @Valid List<InventoryIssueRequestLineDTO> lines) {
}
