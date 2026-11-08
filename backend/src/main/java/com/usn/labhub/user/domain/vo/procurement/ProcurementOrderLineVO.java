package com.usn.labhub.user.domain.vo.procurement;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcurementOrderLineVO {
    private Long id;
    private Integer lineNo;
    private Long componentId;
    private String componentName;
    private String manufacturerName;
    private String mpn;
    private Long componentSpecSnapshotId;
    private Integer specVersion;
    private String unit;
    private BigDecimal orderedQuantity;
    private BigDecimal unitPrice;
    private BigDecimal lineSubtotal;
    private BigDecimal allocatedNetAmount;
    private BigDecimal effectiveUnitCost;
    private BigDecimal receivedQuantity;
    private BigDecimal canceledQuantity;
    private BigDecimal remainingQuantity;
    private Integer version;
}
