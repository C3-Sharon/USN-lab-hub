package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryIssuePolicyOverrideRecord {
    private Long id;
    private Long policyId;
    private Long componentId;
    private BigDecimal directQuantityLimit;
    private String componentName;
    private String unitSnapshot;
    private Long createdBy;
    private LocalDateTime createTime;
}
