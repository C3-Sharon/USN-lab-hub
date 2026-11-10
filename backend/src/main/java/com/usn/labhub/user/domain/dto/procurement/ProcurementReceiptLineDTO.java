package com.usn.labhub.user.domain.dto.procurement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProcurementReceiptLineDTO(
        Long orderLineId,
        BigDecimal quantity,
        String supplierLotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String locationNote,
        String qualityNote) {
}
