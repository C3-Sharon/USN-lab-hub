package com.usn.labhub.user.domain.dto.procurement;

import java.math.BigDecimal;

public record ProcurementMarkOrderedDTO(
        Integer version,
        Long buyerId,
        String supplierName,
        String platformType,
        String platformOrderNo,
        BigDecimal actualPaidAmount,
        String paymentDifferenceNote) {
}
