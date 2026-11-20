package com.usn.labhub.user.domain.vo.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryIssueLineVO {
    private Long id;
    private Long requestId;
    private Integer lineNo;
    private Long componentId;
    private String componentName;
    private String manufacturerName;
    private String mpn;
    private String unit;
    private BigDecimal requestedQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal issuedQuantity;
    private BigDecimal returnedQuantity;
    private BigDecimal estimatedAmount;
    private Integer version;
}
