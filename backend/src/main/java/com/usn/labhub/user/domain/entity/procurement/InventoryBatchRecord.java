package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryBatchRecord {
    private Long id;
    private String batchNo;
    private Long componentId;
    private Long sourceReceiptLineId;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitCost;
    private String supplierLotNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String locationNote;
    private String qualityNote;
    private Long createdBy;
    private LocalDateTime createTime;
}
