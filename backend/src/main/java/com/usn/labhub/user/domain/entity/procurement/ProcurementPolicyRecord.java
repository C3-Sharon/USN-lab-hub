package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProcurementPolicyRecord {
    private Long id;
    private Integer policyVersion;
    private String currency;
    private BigDecimal approvalThreshold;
    private BigDecimal valuableUnitPriceThreshold;
    private LocalDateTime effectiveAt;
    private Long createdBy;
    private LocalDateTime createTime;
    private List<Long> forcedCategoryIds;
}
