package com.usn.labhub.user.domain.vo.procurement;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryBalanceVO {
    private Long componentId;
    private String componentName;
    private String status;
    private BigDecimal onHandQuantity;
    private String unit;
    private Integer version;
    private LocalDateTime updateTime;
}
