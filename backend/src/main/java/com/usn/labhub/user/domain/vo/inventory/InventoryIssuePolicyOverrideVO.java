package com.usn.labhub.user.domain.vo.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryIssuePolicyOverrideVO {
    private Long id;
    private Long policyId;
    private Long componentId;
    private String componentName;
    private String unit;
    private BigDecimal directQuantityLimit;
}
