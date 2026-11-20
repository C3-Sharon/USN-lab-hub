package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryIssueReservationAllocationRecord {
    private Long id;
    private Long reservationId;
    private Long batchId;
    private BigDecimal quantity;
    private BigDecimal unitCostSnapshot;
    private Integer version;
}
