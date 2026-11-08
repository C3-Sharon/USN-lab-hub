package com.usn.labhub.user.domain.entity.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryBalanceRecord {
    private Long componentId;
    private BigDecimal onHandQuantity;
    private String unit;
    private Integer version;
    private LocalDateTime updateTime;
}
