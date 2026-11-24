package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryIssueFulfillmentLineRecord {
    private Long id;
    private Long fulfillmentId;
    private Long requestLineId;
    private Long reservationAllocationId;
    private Long batchId;
    private BigDecimal quantity;
    private BigDecimal unitCostSnapshot;
    private Long movementId;
}
