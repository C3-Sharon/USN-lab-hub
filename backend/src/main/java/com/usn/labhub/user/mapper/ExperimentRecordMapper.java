package com.usn.labhub.user.mapper;

import com.usn.labhub.user.domain.entity.experiment.ExperimentFeedbackRow;
import com.usn.labhub.user.domain.entity.experiment.ExperimentRecordRow;
import com.usn.labhub.user.domain.entity.experiment.ExperimentRevisionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExperimentRecordMapper {
    ExperimentRecordRow selectTemplateContext(@Param("versionId") Long versionId);
    Long selectLearningUnitTemplateVersion(@Param("unitId") Long unitId);
    String selectProjectRole(@Param("projectId") Long projectId, @Param("userId") Long userId);
    String selectProjectStatus(@Param("projectId") Long projectId);
    Long lockRecord(@Param("recordId") Long recordId);
    boolean hasProjectMemberFeedback(@Param("recordId") Long recordId);
    String selectUserName(@Param("userId") Long userId);

    int insertRecord(ExperimentRecordRow record);
    int assignRecordCode(@Param("id") Long id, @Param("recordCode") String recordCode);
    int insertRevision(ExperimentRevisionRow revision);
    int setInitialRevision(@Param("recordId") Long recordId, @Param("revisionId") Long revisionId);

    ExperimentRecordRow selectRecord(@Param("recordId") Long recordId, @Param("userId") Long userId);
    long countVisible(@Param("userId") Long userId, @Param("globalRead") boolean globalRead,
                      @Param("projectId") Long projectId, @Param("status") String status,
                      @Param("templateId") Long templateId, @Param("authorId") Long authorId);
    List<ExperimentRecordRow> selectVisiblePage(@Param("userId") Long userId,
                                                 @Param("globalRead") boolean globalRead,
                                                 @Param("projectId") Long projectId,
                                                 @Param("status") String status,
                                                 @Param("templateId") Long templateId,
                                                 @Param("authorId") Long authorId,
                                                 @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);

    ExperimentRevisionRow selectRevision(@Param("recordId") Long recordId,
                                         @Param("revisionId") Long revisionId);
    List<ExperimentRevisionRow> selectRevisions(@Param("recordId") Long recordId,
                                                @Param("offset") int offset,
                                                @Param("pageSize") int pageSize);
    long countRevisions(@Param("recordId") Long recordId);
    int updateEditableRevision(ExperimentRevisionRow revision);
    int touchRecord(@Param("recordId") Long recordId, @Param("expectedVersion") Integer expectedVersion,
                    @Param("now") LocalDateTime now);
    int submitRevision(@Param("revisionId") Long revisionId,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("actorId") Long actorId, @Param("now") LocalDateTime now);
    int moveRecord(@Param("recordId") Long recordId, @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus, @Param("expectedVersion") Integer expectedVersion,
                   @Param("currentRevisionId") Long currentRevisionId,
                   @Param("publishedRevisionId") Long publishedRevisionId,
                   @Param("now") LocalDateTime now);
    int changeRevisionStatus(@Param("revisionId") Long revisionId, @Param("fromStatus") String fromStatus,
                             @Param("toStatus") String toStatus, @Param("actorId") Long actorId,
                             @Param("now") LocalDateTime now);
    int insertFeedback(ExperimentFeedbackRow feedback);
    int cloneRevision(@Param("sourceRevisionId") Long sourceRevisionId,
                      @Param("nextRevisionNo") Integer nextRevisionNo,
                      @Param("changeSummary") String changeSummary,
                      @Param("createdBy") Long createdBy, @Param("now") LocalDateTime now);
    Long selectLastInsertedRevision(@Param("recordId") Long recordId,
                                    @Param("revisionNo") Integer revisionNo);
    int markPublished(@Param("revisionId") Long revisionId, @Param("actorId") Long actorId,
                      @Param("now") LocalDateTime now);
    int supersedePublished(@Param("revisionId") Long revisionId);
    int linkConvertedProject(@Param("recordId") Long recordId,
                             @Param("expectedVersion") Integer expectedVersion,
                             @Param("publishedRevisionId") Long publishedRevisionId,
                             @Param("projectId") Long projectId,
                             @Param("now") LocalDateTime now);

    long countFeedback(@Param("recordId") Long recordId, @Param("userId") Long userId,
                       @Param("reviewerView") boolean reviewerView);
    List<ExperimentFeedbackRow> selectFeedback(@Param("recordId") Long recordId,
                                               @Param("userId") Long userId,
                                               @Param("reviewerView") boolean reviewerView,
                                               @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);

    int claimIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                         @Param("idempotencyKey") String idempotencyKey,
                         @Param("requestDigest") String requestDigest,
                         @Param("now") LocalDateTime now);
    com.usn.labhub.user.domain.entity.experiment.IdempotencyRow selectIdempotency(
            @Param("actorId") Long actorId, @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);
    int completeIdempotency(@Param("actorId") Long actorId, @Param("operation") String operation,
                            @Param("idempotencyKey") String idempotencyKey,
                            @Param("responseJson") String responseJson,
                            @Param("now") LocalDateTime now);
    int insertAudit(@Param("requestId") String requestId, @Param("actorId") Long actorId,
                    @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") Long targetId, @Param("projectId") Long projectId,
                    @Param("previousStatus") String previousStatus,
                    @Param("newStatus") String newStatus, @Param("reason") String reason,
                    @Param("detailsJson") String detailsJson, @Param("now") LocalDateTime now);
}
