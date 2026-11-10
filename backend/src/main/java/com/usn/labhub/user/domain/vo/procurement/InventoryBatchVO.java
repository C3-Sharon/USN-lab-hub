package com.usn.labhub.user.domain.vo.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryBatchVO {
    private Long id;
    private String batchNo;
    private Long componentId;
    private Long sourceReceiptLineId;
    private BigDecimal initialQuantity;
    private BigDecimal currentQuantity;
    private String unit;
    private BigDecimal unitCost;
    private String supplierLotNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String locationNote;
    private String qualityNote;
    private String status;
    private LocalDateTime createTime;
}
