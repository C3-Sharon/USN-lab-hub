package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryReturnLineSummaryRecord {
    private Long fulfillmentLineId;
    private String status;
    private BigDecimal quantity;
}
