package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProcurementReceiptLineRecord {
    private Long id;
    private Long receiptId;
    private Long orderLineId;
    private Long componentId;
    private BigDecimal quantity;
    private String unit;
    private String supplierLotNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String locationNote;
    private String qualityNote;
    private Integer version;
}
