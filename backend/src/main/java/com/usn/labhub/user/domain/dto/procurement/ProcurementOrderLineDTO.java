package com.usn.labhub.user.domain.dto.procurement;

import java.math.BigDecimal;

public record ProcurementOrderLineDTO(
        Long componentId,
        BigDecimal orderedQuantity,
        BigDecimal unitPrice) {
}
