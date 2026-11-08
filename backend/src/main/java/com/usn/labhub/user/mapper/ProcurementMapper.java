package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import com.usn.labhub.user.domain.entity.procurement.ProcurementComponentRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementOrderRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementPolicyRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementSubmitLineRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementReceiptRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementReceiptLineRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryBalanceRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryBatchRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryMovementRecord;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderLineVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementApprovalVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementReceiptVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementReceiptLineVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryBalanceVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryBatchVO;
import com.usn.labhub.user.domain.vo.procurement.InventoryMovementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProcurementMapper {
    int insertOrder(ProcurementOrderRecord order);

    int updateDraftOrder(ProcurementOrderRecord order);

    int insertLine(@Param("orderId") Long orderId, @Param("lineNo") int lineNo,
                   @Param("component") ProcurementComponentRecord component,
                   @Param("quantity") java.math.BigDecimal quantity,
                   @Param("unitPrice") java.math.BigDecimal unitPrice,
                   @Param("lineSubtotal") java.math.BigDecimal lineSubtotal);

    int deleteLines(@Param("orderId") Long orderId);

    ProcurementOrderRecord selectOrder(@Param("id") Long id);

    ProcurementOrderRecord selectOrderForUpdate(@Param("id") Long id);

    ProcurementOrderVO selectOrderView(@Param("id") Long id);

    List<ProcurementOrderLineVO> selectOrderLines(@Param("orderId") Long orderId);

    List<ProcurementApprovalVO> selectApprovals(@Param("orderId") Long orderId);

    ProcurementComponentRecord selectComponentForPurchase(@Param("id") Long id);

    List<ProcurementSubmitLineRecord> selectSubmitLines(@Param("orderId") Long orderId);

    int freezeSubmitLine(ProcurementSubmitLineRecord line);

    ProcurementPolicyRecord selectCurrentPolicy();

    ProcurementPolicyRecord selectCurrentPolicyForUpdate();

    List<ProcurementPolicyRecord> selectPolicyVersions();

    List<Long> selectForcedCategoryIds(@Param("policyId") Long policyId);

    int countActiveCategories(@Param("categoryIds") List<Long> categoryIds);

    int insertPolicy(ProcurementPolicyRecord policy);

    int insertForcedCategory(@Param("policyId") Long policyId, @Param("categoryId") Long categoryId);

    int updateSubmittedOrder(@Param("id") Long id, @Param("version") Integer version,
                             @Param("status") String status, @Param("approvalRequired") boolean approvalRequired,
                             @Param("policyVersion") Integer policyVersion,
                             @Param("reasonsJson") String reasonsJson,
                             @Param("triggerSnapshotJson") String triggerSnapshotJson,
                             @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    int insertApproval(@Param("orderId") Long orderId, @Param("decision") String decision,
                       @Param("policyVersion") Integer policyVersion,
                       @Param("triggerSnapshotJson") String triggerSnapshotJson,
                       @Param("actorType") String actorType, @Param("decidedBy") Long decidedBy,
                       @Param("comment") String comment, @Param("requestId") String requestId,
                       @Param("now") LocalDateTime now);

    Long selectLatestManualApprover(@Param("orderId") Long orderId);

    int updateDecisionState(@Param("id") Long id, @Param("version") Integer version,
                            @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                            @Param("actorId") Long actorId, @Param("now") LocalDateTime now,
                            @Param("closeReason") String closeReason,
                            @Param("completedAt") LocalDateTime completedAt);

    int updateOrderedState(@Param("id") Long id, @Param("version") Integer version,
                           @Param("buyerId") Long buyerId, @Param("buyerName") String buyerName,
                           @Param("supplierName") String supplierName,
                           @Param("platformType") String platformType,
                           @Param("platformOrderNo") String platformOrderNo,
                           @Param("normalizedPlatformOrderNo") String normalizedPlatformOrderNo,
                           @Param("actualPaidAmount") BigDecimal actualPaidAmount,
                           @Param("paymentDifferenceNote") String paymentDifferenceNote,
                           @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    int updateLineAllocation(@Param("id") Long id, @Param("allocatedNetAmount") BigDecimal allocatedNetAmount,
                             @Param("effectiveUnitCost") BigDecimal effectiveUnitCost);

    int cancelRemainingLines(@Param("orderId") Long orderId);

    int countConfirmedReceipts(@Param("orderId") Long orderId);

    int insertReceipt(ProcurementReceiptRecord receipt);

    int insertReceiptLine(@Param("receiptId") Long receiptId,
                          @Param("line") ProcurementReceiptLineRecord line);

    ProcurementReceiptRecord selectReceipt(@Param("id") Long id);

    ProcurementReceiptRecord selectReceiptForUpdate(@Param("id") Long id);

    ProcurementReceiptVO selectReceiptView(@Param("id") Long id);

    List<ProcurementReceiptLineVO> selectReceiptLineViews(@Param("receiptId") Long receiptId);

    List<ProcurementReceiptLineRecord> selectReceiptLines(@Param("receiptId") Long receiptId);

    List<ProcurementReceiptLineRecord> selectReceiptLinesForUpdate(@Param("receiptId") Long receiptId);

    List<ProcurementReceiptVO> selectReceipts(@Param("orderId") Long orderId);

    int updateReceiptDraft(@Param("id") Long id, @Param("version") Integer version,
                           @Param("receivedAt") LocalDateTime receivedAt, @Param("note") String note,
                           @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    int deleteReceiptLines(@Param("receiptId") Long receiptId);

    int touchOrderVersion(@Param("id") Long id, @Param("version") Integer version,
                          @Param("status") String status, @Param("actorId") Long actorId,
                          @Param("now") LocalDateTime now);

    int cancelReceipt(@Param("id") Long id, @Param("version") Integer version,
                      @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    List<ProcurementOrderLineVO> selectOrderLinesForUpdate(@Param("orderId") Long orderId,
                                                           @Param("lineIds") List<Long> lineIds);

    int upsertInventoryBalance(@Param("componentId") Long componentId, @Param("unit") String unit,
                               @Param("now") LocalDateTime now);

    List<InventoryBalanceRecord> selectInventoryBalancesForUpdate(@Param("componentIds") List<Long> componentIds);

    int insertInventoryBatch(InventoryBatchRecord batch);

    int updateInventoryBalance(@Param("componentId") Long componentId,
                               @Param("quantity") BigDecimal quantity, @Param("now") LocalDateTime now);

    int insertInventoryMovement(InventoryMovementRecord movement);

    int updateOrderLineReceived(@Param("id") Long id, @Param("version") Integer version,
                                @Param("quantity") BigDecimal quantity);

    int countOrderLinesWithRemaining(@Param("orderId") Long orderId);

    int confirmReceipt(@Param("id") Long id, @Param("version") Integer version,
                       @Param("receiverId") Long receiverId, @Param("receiverName") String receiverName,
                       @Param("now") LocalDateTime now);

    int updateOrderReceiptStatus(@Param("id") Long id, @Param("version") Integer version,
                                 @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                                 @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    InventoryBalanceVO selectInventoryBalance(@Param("componentId") Long componentId);

    long countInventoryBatches(@Param("componentId") Long componentId);

    List<InventoryBatchVO> selectInventoryBatches(@Param("componentId") Long componentId,
                                                   @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countInventoryMovements(@Param("componentId") Long componentId);

    List<InventoryMovementVO> selectInventoryMovements(@Param("componentId") Long componentId,
                                                        @Param("offset") int offset,
                                                        @Param("pageSize") int pageSize);

    long countOrders(@Param("userId") Long userId, @Param("viewAll") boolean viewAll,
                     @Param("status") String status, @Param("projectId") Long projectId,
                     @Param("requesterId") Long requesterId, @Param("supplierKeyword") String supplierKeyword,
                     @Param("keyword") String keyword);

    List<ProcurementOrderVO> selectOrders(@Param("userId") Long userId, @Param("viewAll") boolean viewAll,
                                          @Param("status") String status, @Param("projectId") Long projectId,
                                          @Param("requesterId") Long requesterId,
                                          @Param("supplierKeyword") String supplierKeyword,
                                          @Param("keyword") String keyword,
                                          @Param("sortColumn") String sortColumn,
                                          @Param("sortOrder") String sortOrder,
                                          @Param("offset") int offset, @Param("pageSize") int pageSize);

    int claimIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                         @Param("key") String key, @Param("digest") String digest,
                         @Param("now") LocalDateTime now);

    IdempotencyRow selectIdempotency(@Param("actorId") Long actorId,
                                     @Param("operation") String operation, @Param("key") String key);

    int completeIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                            @Param("key") String key, @Param("responseJson") String responseJson,
                            @Param("now") LocalDateTime now);

    int insertAudit(@Param("requestId") String requestId, @Param("actorId") Long actorId,
                    @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") Long targetId, @Param("previousStatus") String previousStatus,
                    @Param("newStatus") String newStatus, @Param("reason") String reason,
                    @Param("detailsJson") String detailsJson,
                    @Param("now") LocalDateTime now);
}
