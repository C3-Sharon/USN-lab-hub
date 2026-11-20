package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.inventory.InventoryIssueComponentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueBatchRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueApprovalRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueFulfillmentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueFulfillmentLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyOverrideRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationAllocationRecord;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePolicyOverrideVO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssueRequestVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryIssueMapper {
    InventoryIssuePolicyRecord selectCurrentPolicy();

    InventoryIssuePolicyRecord selectCurrentPolicyForUpdate();

    List<InventoryIssuePolicyRecord> selectPolicyVersions();

    int countActiveCategories(@Param("categoryIds") List<Long> categoryIds);

    int insertPolicy(InventoryIssuePolicyRecord policy);

    int insertForcedCategory(@Param("policyId") Long policyId, @Param("categoryId") Long categoryId);

    List<Long> selectForcedCategoryIds(@Param("policyId") Long policyId);

    int insertOverride(InventoryIssuePolicyOverrideRecord override);

    List<InventoryIssuePolicyOverrideVO> selectOverrides(@Param("policyId") Long policyId);

    InventoryIssueComponentRecord selectComponentForIssue(@Param("id") Long id);

    int insertRequest(InventoryIssueRequestRecord request);

    int updateDraftRequest(InventoryIssueRequestRecord request);

    int insertLine(InventoryIssueLineRecord line);

    int deleteLines(@Param("requestId") Long requestId);

    InventoryIssueRequestRecord selectRequest(@Param("id") Long id);

    InventoryIssueRequestRecord selectRequestForUpdate(@Param("id") Long id);

    List<InventoryIssueLineRecord> selectLinesForUpdate(@Param("requestId") Long requestId);

    List<InventoryIssueBatchRecord> selectBatchesForUpdate(@Param("componentId") Long componentId);

    List<InventoryIssueBatchRecord> selectBatchesForUpdateByIds(
            @Param("batchIds") List<Long> batchIds);

    LocalDateTime selectCurrentTimestamp();

    int submitRequest(@Param("id") Long id, @Param("version") Integer version,
                      @Param("status") String status,
                      @Param("approvalRequired") boolean approvalRequired,
                      @Param("approvalReasonsJson") String approvalReasonsJson,
                      @Param("policyVersion") Integer policyVersion,
                      @Param("estimatedAmount") BigDecimal estimatedAmount,
                      @Param("submittedAt") LocalDateTime submittedAt,
                      @Param("expiresAt") LocalDateTime expiresAt,
                      @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    int decideRequest(@Param("id") Long id, @Param("version") Integer version,
                      @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                      @Param("approvedAt") LocalDateTime approvedAt,
                      @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    int cancelRequest(@Param("id") Long id, @Param("version") Integer version,
                      @Param("status") String status,
                      @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    int expireRequest(@Param("id") Long id, @Param("version") Integer version,
                      @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    int insertApproval(InventoryIssueApprovalRecord approval);

    int insertReservation(InventoryIssueReservationRecord reservation);

    int insertReservationAllocation(InventoryIssueReservationAllocationRecord allocation);

    List<InventoryIssueReservationRecord> selectReservationsForUpdate(
            @Param("requestId") Long requestId);

    List<InventoryIssueReservationAllocationRecord> selectReservationAllocations(
            @Param("reservationIds") List<Long> reservationIds);

    List<InventoryIssueReservationAllocationRecord> selectAllocationsForRequest(
            @Param("requestId") Long requestId);

    int releaseReservation(@Param("id") Long id, @Param("status") String status,
                           @Param("releaseReason") String releaseReason);

    int reserveBalance(@Param("componentId") Long componentId,
                       @Param("delta") BigDecimal delta, @Param("now") LocalDateTime now);

    int releaseBalance(@Param("componentId") Long componentId,
                       @Param("delta") BigDecimal delta, @Param("now") LocalDateTime now);

    int reserveBatch(@Param("batchId") Long batchId, @Param("delta") BigDecimal delta);

    int releaseBatch(@Param("batchId") Long batchId, @Param("delta") BigDecimal delta);

    int reserveLine(@Param("lineId") Long lineId, @Param("delta") BigDecimal delta);

    int releaseLine(@Param("lineId") Long lineId, @Param("delta") BigDecimal delta);

    int deleteAllocationsByReservationIds(@Param("reservationIds") List<Long> reservationIds);

    int insertFulfillment(InventoryIssueFulfillmentRecord fulfillment);

    int insertFulfillmentLine(InventoryIssueFulfillmentLineRecord line);

    InventoryIssueFulfillmentRecord selectFulfillmentByRequest(@Param("requestId") Long requestId);

    List<InventoryIssueFulfillmentLineRecord> selectFulfillmentLines(
            @Param("fulfillmentId") Long fulfillmentId);

    int consumeBalance(@Param("componentId") Long componentId,
                       @Param("delta") BigDecimal delta, @Param("now") LocalDateTime now);

    int consumeBatch(@Param("batchId") Long batchId, @Param("delta") BigDecimal delta);

    int consumeReservation(@Param("id") Long id);

    int consumeLine(@Param("lineId") Long lineId, @Param("delta") BigDecimal delta);

    int issueRequest(@Param("id") Long id, @Param("version") Integer version,
                     @Param("issuedAt") LocalDateTime issuedAt,
                     @Param("actorId") Long actorId, @Param("now") LocalDateTime now);

    List<Long> selectExpiredCandidateIds(@Param("limit") int limit);

    InventoryIssueRequestVO selectRequestView(@Param("id") Long id);

    List<com.usn.labhub.user.domain.vo.inventory.InventoryIssueLineVO> selectLines(
            @Param("requestId") Long requestId);

    long countRequests(@Param("userId") Long userId, @Param("viewAll") boolean viewAll,
                       @Param("status") String status, @Param("projectId") Long projectId,
                       @Param("requesterId") Long requesterId, @Param("componentId") Long componentId,
                       @Param("submittedFrom") LocalDateTime submittedFrom,
                       @Param("submittedTo") LocalDateTime submittedTo,
                       @Param("keyword") String keyword);

    List<InventoryIssueRequestVO> selectRequests(@Param("userId") Long userId,
                                                 @Param("viewAll") boolean viewAll,
                                                 @Param("status") String status,
                                                 @Param("projectId") Long projectId,
                                                 @Param("requesterId") Long requesterId,
                                                 @Param("componentId") Long componentId,
                                                 @Param("submittedFrom") LocalDateTime submittedFrom,
                                                 @Param("submittedTo") LocalDateTime submittedTo,
                                                 @Param("keyword") String keyword,
                                                 @Param("sortColumn") String sortColumn,
                                                 @Param("sortOrder") String sortOrder,
                                                 @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);
}
