package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.component.ComponentCategoryRecord;
import com.usn.labhub.user.domain.entity.component.ComponentManufacturerRecord;
import com.usn.labhub.user.domain.entity.component.ComponentRecord;
import com.usn.labhub.user.domain.entity.component.ComponentSpecCandidateRecord;
import com.usn.labhub.user.domain.entity.component.ComponentSpecSnapshotRecord;
import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import com.usn.labhub.user.domain.vo.component.CategoryPathItemVO;
import com.usn.labhub.user.domain.vo.component.CategoryVO;
import com.usn.labhub.user.domain.vo.component.ComponentVO;
import com.usn.labhub.user.domain.vo.component.ManufacturerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ComponentCatalogMapper {
    int insertCategory(ComponentCategoryRecord row);
    int updateCategoryPath(@Param("id") Long id, @Param("path") String path);
    ComponentCategoryRecord selectCategory(@Param("id") Long id);
    ComponentCategoryRecord selectCategoryForUpdate(@Param("id") Long id);
    ComponentCategoryRecord selectCategoryByCode(@Param("code") String code);
    long countCategoryName(@Param("parentScopeId") Long parentScopeId,
                           @Param("normalizedName") String normalizedName,
                           @Param("excludeId") Long excludeId);
    List<CategoryVO> selectCategories(@Param("allStates") boolean allStates);
    int updateCategory(ComponentCategoryRecord row);
    int updateCategoryStatus(@Param("id") Long id, @Param("version") Integer version,
                             @Param("currentStatus") String currentStatus,
                             @Param("targetStatus") String targetStatus,
                             @Param("updatedBy") Long updatedBy, @Param("now") LocalDateTime now);
    long countActiveChildren(@Param("categoryId") Long categoryId);
    long countBlockingComponentsByCategory(@Param("categoryId") Long categoryId);

    int insertManufacturer(ComponentManufacturerRecord row);
    ComponentManufacturerRecord selectManufacturer(@Param("id") Long id);
    ComponentManufacturerRecord selectManufacturerForUpdate(@Param("id") Long id);
    ComponentManufacturerRecord selectManufacturerByCode(@Param("code") String code);
    long countManufacturerName(@Param("normalizedName") String normalizedName,
                               @Param("excludeId") Long excludeId);
    long countManufacturers(@Param("allStates") boolean allStates,
                            @Param("status") String status, @Param("keyword") String keyword);
    List<ManufacturerVO> selectManufacturers(@Param("allStates") boolean allStates,
                                             @Param("status") String status,
                                             @Param("keyword") String keyword,
                                             @Param("sortColumn") String sortColumn,
                                             @Param("sortOrder") String sortOrder,
                                             @Param("offset") int offset,
                                             @Param("pageSize") int pageSize);
    ManufacturerVO selectManufacturerView(@Param("id") Long id, @Param("allStates") boolean allStates);
    int updateManufacturer(ComponentManufacturerRecord row);
    int updateManufacturerStatus(@Param("id") Long id, @Param("version") Integer version,
                                 @Param("currentStatus") String currentStatus,
                                 @Param("targetStatus") String targetStatus,
                                 @Param("updatedBy") Long updatedBy, @Param("now") LocalDateTime now);
    long countBlockingComponentsByManufacturer(@Param("manufacturerId") Long manufacturerId);

    int insertComponent(ComponentRecord row);
    int updateCatalogCode(@Param("id") Long id, @Param("catalogCode") String catalogCode);
    ComponentRecord selectComponent(@Param("id") Long id);
    ComponentRecord selectComponentForUpdate(@Param("id") Long id);
    ComponentRecord selectDuplicateComponent(@Param("manufacturerId") Long manufacturerId,
                                             @Param("normalizedMpn") String normalizedMpn,
                                             @Param("excludeId") Long excludeId);
    long countComponents(@Param("allStates") boolean allStates, @Param("status") String status,
                         @Param("categoryId") Long categoryId, @Param("categoryPath") String categoryPath,
                         @Param("includeDescendants") boolean includeDescendants,
                         @Param("manufacturerId") Long manufacturerId,
                         @Param("packageType") String packageType, @Param("baseUnit") String baseUnit,
                         @Param("keyword") String keyword);
    List<ComponentVO> selectComponents(@Param("allStates") boolean allStates,
                                       @Param("status") String status,
                                       @Param("categoryId") Long categoryId,
                                       @Param("categoryPath") String categoryPath,
                                       @Param("includeDescendants") boolean includeDescendants,
                                       @Param("manufacturerId") Long manufacturerId,
                                       @Param("packageType") String packageType,
                                       @Param("baseUnit") String baseUnit,
                                       @Param("keyword") String keyword,
                                       @Param("sortColumn") String sortColumn,
                                       @Param("sortOrder") String sortOrder,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);
    ComponentVO selectComponentView(@Param("id") Long id, @Param("allStates") boolean allStates);
    List<CategoryPathItemVO> selectCategoryPath(@Param("categoryId") Long categoryId);
    int updateComponent(ComponentRecord row);
    int updateComponentStatus(@Param("id") Long id, @Param("version") Integer version,
                              @Param("currentStatus") String currentStatus,
                              @Param("targetStatus") String targetStatus,
                              @Param("updatedBy") Long updatedBy, @Param("now") LocalDateTime now);
    int incrementComponentVersion(@Param("id") Long id, @Param("version") Integer version,
                                  @Param("updatedBy") Long updatedBy, @Param("now") LocalDateTime now);
    int updateComponentSnapshotPointer(@Param("id") Long id, @Param("version") Integer version,
                                       @Param("currentSpecVersion") Integer currentSpecVersion,
                                       @Param("snapshotId") Long snapshotId,
                                       @Param("nextSpecVersion") Integer nextSpecVersion,
                                       @Param("updatedBy") Long updatedBy,
                                       @Param("now") LocalDateTime now);

    int insertSpecCandidate(ComponentSpecCandidateRecord row);
    ComponentSpecCandidateRecord selectSpecCandidate(@Param("id") Long id);
    ComponentSpecCandidateRecord selectSpecCandidateForUpdate(@Param("id") Long id);
    long countSpecCandidates(@Param("componentId") Long componentId, @Param("status") String status);
    List<ComponentSpecCandidateRecord> selectSpecCandidates(@Param("componentId") Long componentId,
                                                            @Param("status") String status,
                                                            @Param("offset") int offset,
                                                            @Param("pageSize") int pageSize);
    int decideSpecCandidate(@Param("id") Long id, @Param("version") Integer version,
                            @Param("targetStatus") String targetStatus,
                            @Param("decidedBy") Long decidedBy,
                            @Param("decidedAt") LocalDateTime decidedAt,
                            @Param("decisionNote") String decisionNote);

    int insertSpecSnapshot(ComponentSpecSnapshotRecord row);
    ComponentSpecSnapshotRecord selectSpecSnapshot(@Param("id") Long id);
    long countSpecSnapshots(@Param("componentId") Long componentId,
                            @Param("visibleSnapshotId") Long visibleSnapshotId);
    List<ComponentSpecSnapshotRecord> selectSpecSnapshots(@Param("componentId") Long componentId,
                                                          @Param("visibleSnapshotId") Long visibleSnapshotId,
                                                          @Param("offset") int offset,
                                                          @Param("pageSize") int pageSize);

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
                    @Param("detailsJson") String detailsJson, @Param("now") LocalDateTime now);
}
