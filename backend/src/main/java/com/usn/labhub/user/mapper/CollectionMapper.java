package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.collection.CollectionBatchRow;
import com.usn.labhub.user.domain.entity.collection.CollectionPlanRow;
import com.usn.labhub.user.domain.entity.collection.CollectionRecordSummaryRow;
import com.usn.labhub.user.domain.entity.collection.CollectionRecordRow;
import com.usn.labhub.user.domain.entity.collection.CollectionSnapshotRow;
import com.usn.labhub.user.domain.entity.collection.DataImportRevisionRow;
import com.usn.labhub.user.domain.entity.collection.DataImportRow;
import com.usn.labhub.user.domain.entity.collection.ManualRecordRevisionRow;
import com.usn.labhub.user.domain.entity.collection.ManualRecordRow;
import com.usn.labhub.user.domain.entity.collection.ExperimentSnapshotBindingRow;
import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CollectionMapper {
    CollectionPlanRow selectProjectContext(@Param("projectId") Long projectId, @Param("userId") Long userId);
    String selectProjectRole(@Param("projectId") Long projectId, @Param("userId") Long userId);
    Long selectExperimentProject(@Param("recordId") Long recordId);
    int insertPlan(CollectionPlanRow plan);
    int assignPlanCode(@Param("id") Long id, @Param("code") String code);
    CollectionPlanRow selectPlan(@Param("id") Long id, @Param("userId") Long userId);
    long countPlans(@Param("projectId") Long projectId, @Param("status") String status,
                    @Param("sourceType") String sourceType);
    List<CollectionPlanRow> selectPlans(@Param("projectId") Long projectId, @Param("userId") Long userId,
                                        @Param("status") String status, @Param("sourceType") String sourceType,
                                        @Param("offset") int offset, @Param("pageSize") int pageSize);
    int updateDraftPlan(CollectionPlanRow plan);
    int movePlan(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                 @Param("toStatus") String toStatus, @Param("expectedVersion") Integer expectedVersion,
                 @Param("now") LocalDateTime now);

    int insertBatch(CollectionBatchRow batch);
    int assignBatchCode(@Param("id") Long id, @Param("code") String code);
    CollectionBatchRow selectBatch(@Param("id") Long id, @Param("userId") Long userId);
    long countBatches(@Param("planId") Long planId, @Param("status") String status);
    List<CollectionBatchRow> selectBatches(@Param("planId") Long planId, @Param("userId") Long userId,
                                           @Param("status") String status, @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);
    Long selectConfirmedSourceRevision(@Param("batchId") Long batchId);
    CollectionRecordSummaryRow selectRecordSummary(@Param("batchId") Long batchId);
    List<Map<String, Object>> selectChecksumRows(@Param("batchId") Long batchId);
    int completeBatch(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                      @Param("recordCount") long recordCount, @Param("rejectedCount") long rejectedCount,
                      @Param("observedFrom") LocalDateTime observedFrom,
                      @Param("observedTo") LocalDateTime observedTo,
                      @Param("sourceRevisionId") Long sourceRevisionId,
                      @Param("completionReason") String completionReason,
                      @Param("checksum") String checksum, @Param("now") LocalDateTime now);
    int moveBatch(@Param("id") Long id, @Param("fromStatuses") List<String> fromStatuses,
                  @Param("toStatus") String toStatus, @Param("expectedVersion") Integer expectedVersion,
                  @Param("completionReason") String completionReason, @Param("now") LocalDateTime now);

    Long lockBatch(@Param("id") Long id);
    DataImportRow selectImportByBatch(@Param("batchId") Long batchId);
    DataImportRow selectImport(@Param("id") Long id);
    int insertImport(DataImportRow row);
    int markBatchProcessing(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                            @Param("now") LocalDateTime now);
    DataImportRevisionRow selectImportRevision(@Param("id") Long id);
    List<DataImportRevisionRow> selectImportRevisions(@Param("importId") Long importId);
    int insertImportRevision(DataImportRevisionRow row);
    int setCurrentImportRevision(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                                 @Param("revisionId") Long revisionId, @Param("now") LocalDateTime now);
    int supersedeConfirmedImportRevisions(@Param("importId") Long importId,
                                          @Param("exceptId") Long exceptId);
    int confirmImportRevision(@Param("id") Long id, @Param("actorId") Long actorId,
                              @Param("recordCount") long recordCount, @Param("rejectedCount") long rejectedCount,
                              @Param("now") LocalDateTime now);
    int confirmImport(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                      @Param("revisionId") Long revisionId, @Param("now") LocalDateTime now);
    int markBatchReviewRequired(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                                @Param("now") LocalDateTime now);
    int insertCollectionRecord(CollectionRecordRow row);

    ManualRecordRow selectManualRecord(@Param("id") Long id);
    int insertManualRecord(ManualRecordRow row);
    int insertManualRevision(ManualRecordRevisionRow row);
    int setInitialManualRevision(@Param("id") Long id, @Param("revisionId") Long revisionId,
                                 @Param("now") LocalDateTime now);
    int setCurrentManualRevision(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                                 @Param("revisionId") Long revisionId, @Param("now") LocalDateTime now);
    List<ManualRecordRevisionRow> selectManualRevisions(@Param("recordId") Long recordId);

    CollectionRecordSummaryRow selectEffectiveRecordSummary(@Param("batchId") Long batchId,
                                                             @Param("sourceType") String sourceType,
                                                             @Param("sourceRevisionId") Long sourceRevisionId);
    List<Map<String, Object>> selectEffectiveChecksumRows(@Param("batchId") Long batchId,
                                                          @Param("sourceType") String sourceType,
                                                          @Param("sourceRevisionId") Long sourceRevisionId);
    List<CollectionRecordRow> selectEffectiveRecords(@Param("batchId") Long batchId,
                                                     @Param("sourceType") String sourceType,
                                                     @Param("sourceRevisionId") Long sourceRevisionId,
                                                     @Param("cursorTime") LocalDateTime cursorTime,
                                                     @Param("cursorId") Long cursorId,
                                                     @Param("limit") int limit);

    CollectionSnapshotRow selectSnapshotByBatch(@Param("batchId") Long batchId);
    CollectionSnapshotRow selectSnapshot(@Param("id") Long id);
    int insertSnapshot(CollectionSnapshotRow row);
    int insertSnapshotManualRevisions(@Param("snapshotId") Long snapshotId,
                                      @Param("batchId") Long batchId);
    String selectImportMappingDigest(@Param("revisionId") Long revisionId);

    ExperimentSnapshotBindingRow selectExperimentRevisionBinding(@Param("recordId") Long recordId,
                                                                  @Param("revisionId") Long revisionId,
                                                                  @Param("userId") Long userId);
    int updateExperimentSnapshotBinding(@Param("recordId") Long recordId,
                                        @Param("revisionId") Long revisionId,
                                        @Param("recordVersion") Integer recordVersion,
                                        @Param("revisionVersion") Integer revisionVersion,
                                        @Param("snapshotId") Long snapshotId,
                                        @Param("now") LocalDateTime now);
    int touchExperimentRecord(@Param("recordId") Long recordId,
                              @Param("recordVersion") Integer recordVersion,
                              @Param("now") LocalDateTime now);

    int claimIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                         @Param("key") String key, @Param("digest") String digest,
                         @Param("now") LocalDateTime now);
    IdempotencyRow selectIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                                     @Param("key") String key);
    int completeIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                            @Param("key") String key, @Param("responseJson") String responseJson,
                            @Param("now") LocalDateTime now);
    int insertAudit(@Param("requestId") String requestId, @Param("actorId") Long actorId,
                    @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") Long targetId, @Param("projectId") Long projectId,
                    @Param("previousStatus") String previousStatus, @Param("newStatus") String newStatus,
                    @Param("reason") String reason, @Param("detailsJson") String detailsJson,
                    @Param("now") LocalDateTime now);
}
