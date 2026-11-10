package com.usn.labhub.user.domain.dto.procurement;

import java.math.BigDecimal;
import java.util.List;

public record ProcurementPolicyUpdateDTO(
        Integer currentPolicyVersion,
        BigDecimal approvalThreshold,
        BigDecimal valuableUnitPriceThreshold,
        List<Long> forcedCategoryIds) {
}
