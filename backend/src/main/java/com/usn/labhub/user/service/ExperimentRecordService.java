package com.usn.labhub.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.experiment.ExperimentFeedbackDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentPublishDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentRecordCreateDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentRevisionCreateDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentRevisionUpdateDTO;
import com.usn.labhub.user.domain.dto.experiment.ExperimentSubmitDTO;
import com.usn.labhub.user.domain.entity.experiment.ExperimentFeedbackRow;
import com.usn.labhub.user.domain.entity.experiment.ExperimentRecordRow;
import com.usn.labhub.user.domain.entity.experiment.ExperimentRevisionRow;
import com.usn.labhub.user.domain.entity.experiment.IdempotencyRow;
import com.usn.labhub.user.experiment.ExperimentApiException;
import com.usn.labhub.user.learning.template.ExperimentTemplateSchemaValidator;
import com.usn.labhub.user.learning.template.TemplateValidationResult;
import com.usn.labhub.user.mapper.ExperimentRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ExperimentRecordService {
    private static final Set<String> RECORD_STATUSES = Set.of(
            "DRAFT", "IN_REVIEW", "CHANGES_REQUESTED", "APPROVED", "PUBLISHED", "REVISING");
    private static final Set<String> PROJECT_AUTHORS = Set.of("OWNER", "MAINTAINER", "MEMBER");
    private static final Set<String> PROJECT_REVIEWERS = Set.of("OWNER", "MAINTAINER");

    private final ExperimentRecordMapper mapper;
    private final ObjectMapper objectMapper;
    private final ExperimentTemplateSchemaValidator schemaValidator;

    public ExperimentRecordService(ExperimentRecordMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.schemaValidator = new ExperimentTemplateSchemaValidator(objectMapper);
    }

    @Transactional
    public Map<String, Object> create(String idempotencyKey, ExperimentRecordCreateDTO request) {
        return idempotent("CREATE_EXPERIMENT", idempotencyKey, request,
                () -> createRecord(request, idempotencyKey.trim()));
    }

    public Map<String, Object> list(Long projectId, String status, Long templateId, Long authorId,
                                    int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100
                || projectId != null && projectId < 1
                || templateId != null && templateId < 1
                || authorId != null && authorId < 1
                || status != null && !RECORD_STATUSES.contains(status)) {
            throw ExperimentApiException.invalid();
        }
        Long userId = requiredUserId();
        boolean globalRead = isGlobalReviewer();
        long total = mapper.countVisible(userId, globalRead, projectId, status, templateId, authorId);
        List<Map<String, Object>> records = total == 0 ? List.of() : mapper.selectVisiblePage(
                userId, globalRead, projectId, status, templateId, authorId,
                (page - 1) * pageSize, pageSize).stream().map(this::recordSummary).toList();
        return page(total, page, pageSize, records);
    }

    public Map<String, Object> detail(Long recordId) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        Map<String, Object> result = recordDetail(record);
        result.put("currentRevision", revisionView(requireRevision(record, record.getCurrentRevisionId()), false));
        result.put("publishedRevision", record.getPublishedRevisionId() == null ? null
                : revisionView(requireRevision(record, record.getPublishedRevisionId()), false));
        return result;
    }

    @Transactional
    public Map<String, Object> updateRevision(Long recordId, Long revisionId,
                                              ExperimentRevisionUpdateDTO request) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        requireAuthor(record);
        ExperimentRevisionRow revision = requireRevision(record, revisionId);
        if (!revisionId.equals(record.getCurrentRevisionId()) || !"EDITABLE".equals(revision.getStatus())) {
            throw ExperimentApiException.revisionImmutable();
        }
        requireVersions(record, request.getRecordVersion(), revision, request.getRevisionVersion());
        validateContent(record.getSchemaJson(), request.getContentJson());
        if (revision.getRevisionNo() > 1 && !StringUtils.hasText(request.getChangeSummary())) {
            throw ExperimentApiException.invalid();
        }

        copyRevisionFields(revision, request);
        revision.setContentJson(writeJson(request.getContentJson()));
        revision.setVersion(request.getRevisionVersion());
        revision.setUpdateTime(LocalDateTime.now());
        if (mapper.updateEditableRevision(revision) != 1
                || mapper.touchRecord(recordId, request.getRecordVersion(), revision.getUpdateTime()) != 1) {
            throw versionConflict(recordId);
        }
        return revisionAction(recordId, request.getRecordVersion() + 1, revisionId,
                request.getRevisionVersion() + 1, "EDITABLE");
    }

    public Map<String, Object> revisions(Long recordId, int page, int pageSize) {
        requireVisibleRecord(recordId);
        validatePage(page, pageSize);
        long total = mapper.countRevisions(recordId);
        List<Map<String, Object>> list = total == 0 ? List.of() : mapper.selectRevisions(
                recordId, (page - 1) * pageSize, pageSize).stream()
                .map(row -> revisionView(row, false)).toList();
        return page(total, page, pageSize, list);
    }

    public Map<String, Object> revisionDetail(Long recordId, Long revisionId) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        return revisionView(requireRevision(record, revisionId), true);
    }

    @Transactional
    public Map<String, Object> submit(Long recordId, String idempotencyKey, ExperimentSubmitDTO request) {
        return idempotent("SUBMIT_EXPERIMENT:" + recordId, idempotencyKey, request,
                () -> submitCurrent(recordId, request, idempotencyKey));
    }

    @Transactional
    public Map<String, Object> feedback(Long recordId, String idempotencyKey, ExperimentFeedbackDTO request) {
        return idempotent("FEEDBACK_EXPERIMENT:" + recordId, idempotencyKey, request,
                () -> addFeedback(recordId, request, idempotencyKey));
    }

    public Map<String, Object> feedbackList(Long recordId, int page, int pageSize) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        validatePage(page, pageSize);
        boolean reviewerView = record.getAuthorId().equals(requiredUserId()) || canReview(record);
        long total = mapper.countFeedback(recordId, requiredUserId(), reviewerView);
        List<Map<String, Object>> list = total == 0 ? List.of() : mapper.selectFeedback(
                recordId, requiredUserId(), reviewerView, (page - 1) * pageSize, pageSize).stream()
                .map(this::feedbackView).toList();
        return page(total, page, pageSize, list);
    }

    @Transactional
    public Map<String, Object> publish(Long recordId, String idempotencyKey, ExperimentPublishDTO request) {
        return idempotent("PUBLISH_EXPERIMENT:" + recordId, idempotencyKey, request,
                () -> publishApproved(recordId, request, idempotencyKey));
    }

    @Transactional
    public Map<String, Object> createRevision(Long recordId, String idempotencyKey,
                                              ExperimentRevisionCreateDTO request) {
        return idempotent("REVISE_EXPERIMENT:" + recordId, idempotencyKey, request,
                () -> createRevisionFromPublished(recordId, request, idempotencyKey));
    }

    private Map<String, Object> createRecord(ExperimentRecordCreateDTO request, String requestId) {
        Long actorId = requiredUserId();
        if (request.getProjectId() == null && request.getLearningUnitId() == null) {
            throw ExperimentApiException.invalid();
        }
        ExperimentRecordRow template = mapper.selectTemplateContext(request.getTemplateVersionId());
        if (template == null || !"PUBLISHED".equals(template.getStatus())) {
            throw ExperimentApiException.templateMismatch();
        }
        requireCreateContext(request, template, actorId);
        validateContent(template.getSchemaJson(), request.getContentJson());

        LocalDateTime now = LocalDateTime.now();
        ExperimentRecordRow record = new ExperimentRecordRow();
        record.setRecordCode("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        record.setTitle(request.getTitle().trim());
        record.setSummary(trim(request.getSummary()));
        record.setAuthorId(actorId);
        record.setProjectId(request.getProjectId());
        record.setLearningUnitId(request.getLearningUnitId());
        record.setTemplateId(template.getTemplateId());
        record.setTemplateVersionId(template.getTemplateVersionId());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mapper.insertRecord(record);
        String code = "ER-" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "-" + String.format("%06d", record.getId());
        mapper.assignRecordCode(record.getId(), code);

        ExperimentRevisionRow revision = initialRevision(record.getId(), actorId, request, now);
        mapper.insertRevision(revision);
        mapper.setInitialRevision(record.getId(), revision.getId());
        record.setProjectId(request.getProjectId());
        audit(requestId, "CREATE", record, revision.getId(), null, "DRAFT", null, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("recordCode", code);
        result.put("status", "DRAFT");
        result.put("templateVersionId", template.getTemplateVersionId());
        result.put("currentRevision", revisionSummary(revision));
        result.put("publishedRevisionId", null);
        result.put("version", 1);
        return result;
    }

    private Map<String, Object> submitCurrent(Long recordId, ExperimentSubmitDTO request, String requestId) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        requireAuthor(record);
        ExperimentRevisionRow revision = requireRevision(record, request.getRevisionId());
        requireVersions(record, request.getRecordVersion(), revision, request.getRevisionVersion());
        if (!request.getRevisionId().equals(record.getCurrentRevisionId())
                || !"EDITABLE".equals(revision.getStatus())
                || !("DRAFT".equals(record.getStatus()) || "CHANGES_REQUESTED".equals(record.getStatus())
                || "REVISING".equals(record.getStatus()))) {
            throw ExperimentApiException.invalidTransition();
        }
        validateContent(record.getSchemaJson(), readJson(revision.getContentJson()));
        LocalDateTime now = LocalDateTime.now();
        if (mapper.submitRevision(revision.getId(), request.getRevisionVersion(), requiredUserId(), now) != 1
                || mapper.moveRecord(recordId, record.getStatus(), "IN_REVIEW", request.getRecordVersion(),
                null, null, now) != 1) {
            throw versionConflict(recordId);
        }
        audit(requestId, "SUBMIT", record, revision.getId(), record.getStatus(), "IN_REVIEW", null, now);
        return stateAction(recordId, request.getRecordVersion() + 1, revision.getId(), "SUBMITTED", "IN_REVIEW");
    }

    private Map<String, Object> addFeedback(Long recordId, ExperimentFeedbackDTO request, String requestId) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        requireReviewer(record);
        mapper.lockRecord(recordId);
        record = requireVisibleRecord(recordId);
        if (record.getVersion() != request.getRecordVersion()) {
            throw ExperimentApiException.versionConflict(record.getVersion());
        }
        ExperimentRevisionRow revision = requireRevision(record, request.getRevisionId());
        validateFeedback(record, revision, request);
        LocalDateTime now = LocalDateTime.now();
        ExperimentFeedbackRow feedback = new ExperimentFeedbackRow();
        feedback.setRecordId(recordId);
        feedback.setRevisionId(revision.getId());
        feedback.setType(request.getType());
        feedback.setContent(trim(request.getContent()));
        feedback.setVisibility(request.getVisibility());
        feedback.setReviewerId(requiredUserId());
        feedback.setReviewerName(mapper.selectUserName(requiredUserId()));
        feedback.setRequestId(requestId);
        feedback.setCreateTime(now);
        mapper.insertFeedback(feedback);

        if ("COMMENT".equals(request.getType())) {
            if (mapper.touchRecord(recordId, request.getRecordVersion(), now) != 1) {
                throw versionConflict(recordId);
            }
            audit(requestId, "COMMENT", record, revision.getId(), record.getStatus(), record.getStatus(),
                    null, now);
            Map<String, Object> result = stateAction(recordId, request.getRecordVersion() + 1,
                    revision.getId(), revision.getStatus(), record.getStatus());
            result.put("feedbackId", feedback.getId());
            return result;
        }
        if (!"IN_REVIEW".equals(record.getStatus()) || !"SUBMITTED".equals(revision.getStatus())
                || !revision.getId().equals(record.getCurrentRevisionId())) {
            throw ExperimentApiException.invalidTransition();
        }
        if ("APPROVAL".equals(request.getType())) {
            if (mapper.changeRevisionStatus(revision.getId(), "SUBMITTED", "APPROVED",
                    requiredUserId(), now) != 1
                    || mapper.moveRecord(recordId, "IN_REVIEW", "APPROVED", request.getRecordVersion(),
                    null, null, now) != 1) {
                throw versionConflict(recordId);
            }
            audit(requestId, "APPROVE", record, revision.getId(), "IN_REVIEW", "APPROVED", null, now);
            Map<String, Object> result = stateAction(recordId, request.getRecordVersion() + 1,
                    revision.getId(), "APPROVED", "APPROVED");
            result.put("feedbackId", feedback.getId());
            return result;
        }

        if (mapper.changeRevisionStatus(revision.getId(), "SUBMITTED", "CHANGES_REQUESTED",
                requiredUserId(), now) != 1) {
            throw ExperimentApiException.invalidTransition();
        }
        int nextNo = revision.getRevisionNo() + 1;
        mapper.cloneRevision(revision.getId(), nextNo, request.getContent().trim(), record.getAuthorId(), now);
        Long newRevisionId = mapper.selectLastInsertedRevision(recordId, nextNo);
        if (newRevisionId == null || mapper.moveRecord(recordId, "IN_REVIEW", "CHANGES_REQUESTED",
                request.getRecordVersion(), newRevisionId, null, now) != 1) {
            throw versionConflict(recordId);
        }
        audit(requestId, "REQUEST_CHANGES", record, revision.getId(), "IN_REVIEW", "CHANGES_REQUESTED",
                request.getContent().trim(), now);
        Map<String, Object> result = stateAction(recordId, request.getRecordVersion() + 1,
                revision.getId(), "CHANGES_REQUESTED", "CHANGES_REQUESTED");
        result.put("feedbackId", feedback.getId());
        result.put("newRevision", Map.of("id", newRevisionId, "revisionNo", nextNo,
                "status", "EDITABLE", "version", 1));
        return result;
    }

    private Map<String, Object> publishApproved(Long recordId, ExperimentPublishDTO request, String requestId) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        if (!canReview(record)) {
            throw ExperimentApiException.feedbackDenied();
        }
        if (record.getVersion() != request.getRecordVersion()) {
            throw ExperimentApiException.versionConflict(record.getVersion());
        }
        ExperimentRevisionRow revision = requireRevision(record, request.getRevisionId());
        if (!"APPROVED".equals(record.getStatus()) || !"APPROVED".equals(revision.getStatus())
                || !revision.getId().equals(record.getCurrentRevisionId())) {
            throw ExperimentApiException.invalidTransition();
        }
        LocalDateTime now = LocalDateTime.now();
        Long previousPublished = record.getPublishedRevisionId();
        if (mapper.markPublished(revision.getId(), requiredUserId(), now) != 1
                || mapper.moveRecord(recordId, "APPROVED", "PUBLISHED", request.getRecordVersion(),
                null, revision.getId(), now) != 1) {
            throw versionConflict(recordId);
        }
        if (previousPublished != null && !previousPublished.equals(revision.getId())) {
            mapper.supersedePublished(previousPublished);
        }
        audit(requestId, "PUBLISH", record, revision.getId(), "APPROVED", "PUBLISHED", null, now);
        return stateAction(recordId, request.getRecordVersion() + 1, revision.getId(), "PUBLISHED", "PUBLISHED");
    }

    private Map<String, Object> createRevisionFromPublished(Long recordId, ExperimentRevisionCreateDTO request,
                                                            String requestId) {
        ExperimentRecordRow record = requireVisibleRecord(recordId);
        requireAuthor(record);
        mapper.lockRecord(recordId);
        record = requireVisibleRecord(recordId);
        if (!"PUBLISHED".equals(record.getStatus()) || record.getPublishedRevisionId() == null) {
            throw ExperimentApiException.invalidTransition();
        }
        if (record.getVersion() != request.getRecordVersion()) {
            throw ExperimentApiException.versionConflict(record.getVersion());
        }
        ExperimentRevisionRow source = requireRevision(record, record.getPublishedRevisionId());
        int nextNo = source.getRevisionNo() + 1;
        LocalDateTime now = LocalDateTime.now();
        mapper.cloneRevision(source.getId(), nextNo, request.getChangeSummary().trim(), requiredUserId(), now);
        Long revisionId = mapper.selectLastInsertedRevision(recordId, nextNo);
        if (revisionId == null || mapper.moveRecord(recordId, "PUBLISHED", "REVISING",
                request.getRecordVersion(), revisionId, null, now) != 1) {
            throw versionConflict(recordId);
        }
        audit(requestId, "CREATE_REVISION", record, revisionId, "PUBLISHED", "REVISING",
                request.getChangeSummary().trim(), now);
        return Map.of("recordId", recordId, "recordVersion", request.getRecordVersion() + 1,
                "status", "REVISING", "newRevision", Map.of("id", revisionId,
                        "revisionNo", nextNo, "status", "EDITABLE", "version", 1));
    }

    private void requireCreateContext(ExperimentRecordCreateDTO request, ExperimentRecordRow template, Long actorId) {
        if (request.getProjectId() != null) {
            String role = mapper.selectProjectRole(request.getProjectId(), actorId);
            String projectStatus = mapper.selectProjectStatus(request.getProjectId());
            if (projectStatus == null || Set.of("PAUSED", "COMPLETED", "ARCHIVED").contains(projectStatus)) {
                throw ExperimentApiException.operationDenied();
            }
            if (!isSystemAdmin() && (role == null || !PROJECT_AUTHORS.contains(role))) {
                throw ExperimentApiException.operationDenied();
            }
            if ("PROJECT".equals(template.getTemplateScope())
                    && !request.getProjectId().equals(template.getTemplateProjectId())) {
                throw ExperimentApiException.templateMismatch();
            }
        } else if ("PROJECT".equals(template.getTemplateScope())) {
            throw ExperimentApiException.templateMismatch();
        }
        if (request.getLearningUnitId() != null) {
            Long boundVersion = mapper.selectLearningUnitTemplateVersion(request.getLearningUnitId());
            if (!request.getTemplateVersionId().equals(boundVersion)) {
                throw ExperimentApiException.templateMismatch();
            }
        }
    }

    private ExperimentRevisionRow initialRevision(Long recordId, Long actorId,
                                                  ExperimentRecordCreateDTO request, LocalDateTime now) {
        ExperimentRevisionRow revision = new ExperimentRevisionRow();
        revision.setRecordId(recordId);
        revision.setRevisionNo(1);
        revision.setStatus("EDITABLE");
        revision.setContentJson(writeJson(request.getContentJson()));
        revision.setPurpose(trim(request.getPurpose()));
        revision.setEnvironment(trim(request.getEnvironment()));
        revision.setSteps(trim(request.getSteps()));
        revision.setResult(trim(request.getResult()));
        revision.setAnomaly(trim(request.getAnomaly()));
        revision.setConclusion(trim(request.getConclusion()));
        revision.setHardwareVersionRef(trim(request.getHardwareVersionRef()));
        revision.setDeviceInstanceRef(trim(request.getDeviceInstanceRef()));
        revision.setFirmwareVersion(trim(request.getFirmwareVersion()));
        revision.setBomVersionRef(trim(request.getBomVersionRef()));
        revision.setCreatedBy(actorId);
        revision.setVersion(1);
        revision.setCreateTime(now);
        revision.setUpdateTime(now);
        return revision;
    }

    private void copyRevisionFields(ExperimentRevisionRow revision, ExperimentRevisionUpdateDTO request) {
        revision.setPurpose(trim(request.getPurpose()));
        revision.setEnvironment(trim(request.getEnvironment()));
        revision.setSteps(trim(request.getSteps()));
        revision.setResult(trim(request.getResult()));
        revision.setAnomaly(trim(request.getAnomaly()));
        revision.setConclusion(trim(request.getConclusion()));
        revision.setHardwareVersionRef(trim(request.getHardwareVersionRef()));
        revision.setDeviceInstanceRef(trim(request.getDeviceInstanceRef()));
        revision.setFirmwareVersion(trim(request.getFirmwareVersion()));
        revision.setBomVersionRef(trim(request.getBomVersionRef()));
        revision.setChangeSummary(trim(request.getChangeSummary()));
    }

    private void validateFeedback(ExperimentRecordRow record, ExperimentRevisionRow revision,
                                  ExperimentFeedbackDTO request) {
        if (record.getAuthorId().equals(requiredUserId())) {
            throw ExperimentApiException.feedbackDenied();
        }
        if (("COMMENT".equals(request.getType()) || "CHANGE_REQUEST".equals(request.getType()))
                && !StringUtils.hasText(request.getContent())) {
            throw ExperimentApiException.invalid();
        }
        if ("PROJECT_MEMBERS".equals(request.getVisibility()) && record.getProjectId() == null) {
            throw ExperimentApiException.invalid();
        }
        if ("EDITABLE".equals(revision.getStatus())) {
            throw ExperimentApiException.invalidTransition();
        }
    }

    private ExperimentRecordRow requireVisibleRecord(Long recordId) {
        if (recordId == null || recordId < 1) {
            throw ExperimentApiException.recordNotFound();
        }
        ExperimentRecordRow record = mapper.selectRecord(recordId, requiredUserId());
        if (record == null || !canRead(record)) {
            throw ExperimentApiException.recordNotFound();
        }
        return record;
    }

    private boolean canRead(ExperimentRecordRow record) {
        if (isGlobalReviewer() || record.getAuthorId().equals(requiredUserId())) {
            return true;
        }
        if (record.getProjectRole() != null && PROJECT_REVIEWERS.contains(record.getProjectRole())) {
            return true;
        }
        return record.getProjectRole() != null && ("PUBLISHED".equals(record.getStatus())
                || mapper.hasProjectMemberFeedback(record.getId()));
    }

    private boolean canReview(ExperimentRecordRow record) {
        return isGlobalReviewer() || record.getProjectRole() != null
                && PROJECT_REVIEWERS.contains(record.getProjectRole());
    }

    private void requireReviewer(ExperimentRecordRow record) {
        if (!canReview(record) || record.getAuthorId().equals(requiredUserId())) {
            throw ExperimentApiException.feedbackDenied();
        }
    }

    private void requireAuthor(ExperimentRecordRow record) {
        if (!record.getAuthorId().equals(requiredUserId())) {
            throw ExperimentApiException.operationDenied();
        }
    }

    private ExperimentRevisionRow requireRevision(ExperimentRecordRow record, Long revisionId) {
        if (revisionId == null || revisionId < 1) {
            throw ExperimentApiException.revisionNotFound();
        }
        ExperimentRevisionRow revision = mapper.selectRevision(record.getId(), revisionId);
        if (revision == null) {
            throw ExperimentApiException.revisionNotFound();
        }
        return revision;
    }

    private void requireVersions(ExperimentRecordRow record, Integer recordVersion,
                                 ExperimentRevisionRow revision, Integer revisionVersion) {
        if (!record.getVersion().equals(recordVersion)) {
            throw ExperimentApiException.versionConflict(record.getVersion());
        }
        if (!revision.getVersion().equals(revisionVersion)) {
            throw ExperimentApiException.versionConflict(revision.getVersion());
        }
    }

    private ExperimentApiException versionConflict(Long recordId) {
        ExperimentRecordRow current = mapper.selectRecord(recordId, requiredUserId());
        return ExperimentApiException.versionConflict(current == null ? 1 : current.getVersion());
    }

    private void validateContent(String schemaJson, JsonNode content) {
        TemplateValidationResult validation = schemaValidator.validateData(readJson(schemaJson), content);
        if (!validation.valid()) {
            throw ExperimentApiException.contentInvalid(validation.errors());
        }
    }

    private <T> Map<String, Object> idempotent(String operation, String key, T request,
                                               Supplier<Map<String, Object>> action) {
        if (!StringUtils.hasText(key) || key.trim().length() > 100) {
            throw ExperimentApiException.invalid();
        }
        String normalizedKey = key.trim();
        Long actorId = requiredUserId();
        String digest = digest(request);
        LocalDateTime now = LocalDateTime.now();
        if (mapper.claimIdempotency(actorId, operation, normalizedKey, digest, now) == 0) {
            IdempotencyRow existing = mapper.selectIdempotency(actorId, operation, normalizedKey);
            if (existing == null || !digest.equals(existing.getRequestDigest())) {
                throw ExperimentApiException.idempotencyReused();
            }
            if (!"COMPLETED".equals(existing.getStatus())) {
                throw ExperimentApiException.invalidTransition();
            }
            return readMap(existing.getResponseJson());
        }
        Map<String, Object> result = action.get();
        mapper.completeIdempotency(actorId, operation, normalizedKey, writeJson(result), LocalDateTime.now());
        return result;
    }

    private void audit(String requestId, String action, ExperimentRecordRow record, Long targetId,
                       String from, String to, String reason, LocalDateTime now) {
        mapper.insertAudit(requestId, requiredUserId(), action, "EXPERIMENT_REVISION", targetId,
                record.getProjectId(), from, to, reason, null, now);
    }

    private Map<String, Object> recordSummary(ExperimentRecordRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("recordCode", row.getRecordCode());
        result.put("title", row.getTitle());
        result.put("summary", row.getSummary());
        result.put("authorId", row.getAuthorId());
        result.put("authorName", row.getAuthorName());
        result.put("projectId", row.getProjectId());
        result.put("projectName", row.getProjectName());
        result.put("learningUnitId", row.getLearningUnitId());
        result.put("learningUnitTitle", row.getLearningUnitTitle());
        result.put("templateId", row.getTemplateId());
        result.put("templateVersionId", row.getTemplateVersionId());
        result.put("templateVersion", row.getTemplateVersion());
        result.put("status", row.getStatus());
        result.put("publishedRevisionId", row.getPublishedRevisionId());
        result.put("version", row.getVersion());
        result.put("createTime", row.getCreateTime());
        result.put("updateTime", row.getUpdateTime());
        return result;
    }

    private Map<String, Object> recordDetail(ExperimentRecordRow row) {
        Map<String, Object> result = recordSummary(row);
        result.put("currentRevisionId", row.getCurrentRevisionId());
        result.put("convertedProjectId", row.getConvertedProjectId());
        return result;
    }

    private Map<String, Object> revisionView(ExperimentRevisionRow row, boolean includeContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("recordId", row.getRecordId());
        result.put("revisionNo", row.getRevisionNo());
        result.put("status", row.getStatus());
        if (includeContent) {
            result.put("contentJson", readJson(row.getContentJson()));
            result.put("purpose", row.getPurpose());
            result.put("environment", row.getEnvironment());
            result.put("steps", row.getSteps());
            result.put("result", row.getResult());
            result.put("anomaly", row.getAnomaly());
            result.put("conclusion", row.getConclusion());
            result.put("hardwareVersionRef", row.getHardwareVersionRef());
            result.put("deviceInstanceRef", row.getDeviceInstanceRef());
            result.put("firmwareVersion", row.getFirmwareVersion());
            result.put("bomVersionRef", row.getBomVersionRef());
            result.put("attachmentRefs", List.of());
        }
        result.put("collectionSnapshotId", row.getCollectionSnapshotId());
        result.put("changeSummary", row.getChangeSummary());
        result.put("previousRevisionId", row.getPreviousRevisionId());
        result.put("submittedBy", row.getSubmittedBy());
        result.put("submittedAt", row.getSubmittedAt());
        result.put("approvedBy", row.getApprovedBy());
        result.put("approvedAt", row.getApprovedAt());
        result.put("publishedBy", row.getPublishedBy());
        result.put("publishedAt", row.getPublishedAt());
        result.put("version", row.getVersion());
        result.put("createTime", row.getCreateTime());
        return result;
    }

    private Map<String, Object> revisionSummary(ExperimentRevisionRow row) {
        return Map.of("id", row.getId(), "revisionNo", row.getRevisionNo(),
                "status", row.getStatus(), "version", row.getVersion());
    }

    private Map<String, Object> feedbackView(ExperimentFeedbackRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("recordId", row.getRecordId());
        result.put("revisionId", row.getRevisionId());
        result.put("type", row.getType());
        result.put("content", row.getContent());
        result.put("visibility", row.getVisibility());
        result.put("reviewerId", row.getReviewerId());
        result.put("reviewerName", row.getReviewerName());
        result.put("createTime", row.getCreateTime());
        return result;
    }

    private Map<String, Object> revisionAction(Long recordId, int recordVersion, Long revisionId,
                                               int revisionVersion, String status) {
        return Map.of("recordId", recordId, "recordVersion", recordVersion,
                "revisionId", revisionId, "revisionVersion", revisionVersion, "status", status);
    }

    private Map<String, Object> stateAction(Long recordId, int recordVersion, Long revisionId,
                                            String revisionStatus, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", recordId);
        result.put("recordVersion", recordVersion);
        result.put("revisionId", revisionId);
        result.put("revisionStatus", revisionStatus);
        result.put("status", status);
        return result;
    }

    private Map<String, Object> page(long total, int page, int pageSize, List<Map<String, Object>> list) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw ExperimentApiException.invalid();
        }
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw ExperimentApiException.invalid();
        }
        return userId;
    }

    private boolean isSystemAdmin() {
        return UserContext.getRoles().contains("SYSTEM_ADMIN");
    }

    private boolean isGlobalReviewer() {
        return isSystemAdmin() || UserContext.getRoles().contains("TEACHER");
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String digest(Object request) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot create request digest", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize experiment data", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read experiment data", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read idempotent response", exception);
        }
    }
}
