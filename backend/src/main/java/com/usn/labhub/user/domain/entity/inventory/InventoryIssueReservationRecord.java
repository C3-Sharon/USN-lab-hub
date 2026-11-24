package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryIssueReservationRecord {
    private Long id;
    private String reservationNo;
    private Long requestId;
    private Long requestLineId;
    private Long componentId;
    private BigDecimal quantity;
    private String status;
    private LocalDateTime expiresAt;
    private String releaseReason;
    private Integer version;
}
