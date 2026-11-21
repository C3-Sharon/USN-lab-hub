package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryReturnLineRecord {
    private Long id;
    private Long returnId;
    private Long fulfillmentLineId;
    private Long batchId;
    private BigDecimal quantity;
}
