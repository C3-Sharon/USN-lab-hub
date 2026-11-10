package com.usn.labhub.user.domain.vo.procurement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProcurementOrderVO {
    private Long id;
    private String purchaseNo;
    private String title;
    private Long requesterId;
    private String requesterName;
    private Long buyerId;
    private String buyerName;
    private Long projectId;
    private String projectName;
    private String fundingSource;
    private String supplierName;
    private String platformType;
    private String platformOrderNo;
    private String currency;
    private BigDecimal itemSubtotal;
    private BigDecimal shippingAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private BigDecimal actualPaidAmount;
    private String paymentDifferenceNote;
    private String status;
    private Boolean approvalRequired;
    private Integer approvalPolicyVersion;
    private List<String> approvalReasons;
    @JsonIgnore
    private String approvalReasonsJson;
    private List<ProcurementApprovalVO> approvalRecords;
    private LocalDateTime orderedAt;
    private LocalDateTime completedAt;
    private String closeReason;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ProcurementOrderLineVO> lines;
}
