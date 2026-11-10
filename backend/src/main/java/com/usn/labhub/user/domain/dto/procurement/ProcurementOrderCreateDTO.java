package com.usn.labhub.user.domain.dto.procurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProcurementOrderCreateDTO(
        @NotBlank @Size(min = 2, max = 120) String title,
        Long projectId,
        @Size(max = 120) String fundingSource,
        BigDecimal shippingAmount,
        BigDecimal discountAmount,
        @Valid List<ProcurementOrderLineDTO> lines) {
}
