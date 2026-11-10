package com.usn.labhub.user.domain.vo.procurement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProcurementReceiptVO {
    private Long id;
    private String receiptNo;
    private Long orderId;
    private String purchaseNo;
    private String status;
    private LocalDateTime receivedAt;
    private Long receiverId;
    private String receiverName;
    private String note;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
    private Integer version;
    private Integer orderVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ProcurementReceiptLineVO> lines;
}
