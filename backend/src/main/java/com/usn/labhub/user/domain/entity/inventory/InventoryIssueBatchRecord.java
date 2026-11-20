package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryIssueBatchRecord {
    private Long id;
    private String batchNo;
    private Long componentId;
    private BigDecimal currentQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal unitCost;
    private String unit;
    private LocalDate expiryDate;
    private LocalDateTime createTime;
    private String status;
}
