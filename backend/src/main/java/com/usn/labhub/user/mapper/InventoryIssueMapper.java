package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.inventory.InventoryIssueComponentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyOverrideRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePolicyOverrideVO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssueRequestVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
