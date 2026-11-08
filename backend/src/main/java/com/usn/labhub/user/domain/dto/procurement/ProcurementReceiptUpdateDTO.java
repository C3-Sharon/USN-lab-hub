package com.usn.labhub.user.domain.dto.procurement;

import java.time.OffsetDateTime;
import java.util.List;

public record ProcurementReceiptUpdateDTO(
        Integer orderVersion,
        Integer receiptVersion,
        OffsetDateTime receivedAt,
        String note,
        List<ProcurementReceiptLineDTO> lines) {
}
