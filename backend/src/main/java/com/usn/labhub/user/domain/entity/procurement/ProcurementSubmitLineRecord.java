package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcurementSubmitLineRecord {
    private Long id;
    private Long componentId;
    private String componentName;
    private String manufacturerName;
    private String mpn;
    private String componentStatus;
    private Long currentSpecSnapshotId;
    private Integer specVersion;
    private Long categoryId;
    private String categoryPath;
    private BigDecimal unitPrice;
    private BigDecimal orderedQuantity;
    private BigDecimal lineSubtotal;
}
