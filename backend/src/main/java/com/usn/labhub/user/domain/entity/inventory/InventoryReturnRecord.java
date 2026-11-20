package com.usn.labhub.user.domain.entity.inventory;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryReturnRecord {
    private Long id;
    private String returnNo;
    private Long fulfillmentId;
    private Long requestId;
    private String status;
    private String reason;
    private Long requestedBy;
    private Long confirmedBy;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createTime;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
