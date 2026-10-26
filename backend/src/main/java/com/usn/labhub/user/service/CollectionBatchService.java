package com.usn.labhub.user.service;

import com.usn.labhub.user.collection.CollectionAccessPolicy;
import com.usn.labhub.user.collection.CollectionApiException;
import com.usn.labhub.user.collection.CollectionSupport;
import com.usn.labhub.user.domain.dto.collection.CollectionBatchCreateDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionVersionDTO;
import com.usn.labhub.user.domain.entity.collection.CollectionBatchRow;
import com.usn.labhub.user.domain.entity.collection.CollectionPlanRow;
import com.usn.labhub.user.domain.entity.collection.CollectionRecordSummaryRow;
import com.usn.labhub.user.domain.entity.collection.CollectionRecordRow;
import com.usn.labhub.user.domain.entity.collection.CollectionSnapshotRow;
import com.usn.labhub.user.domain.entity.collection.DataImportRevisionRow;
import com.usn.labhub.user.domain.entity.collection.DataImportRow;
import com.usn.labhub.user.domain.entity.collection.ManualRecordRow;
import com.usn.labhub.user.mapper.CollectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CollectionBatchService {
    private static final Set<String> STATUSES = Set.of(
            "CREATED", "PROCESSING", "REVIEW_REQUIRED", "COMPLETED", "FAILED", "CANCELED", "ARCHIVED");
    private static final List<String> CANCELABLE = List.of("CREATED", "PROCESSING", "REVIEW_REQUIRED");
    private final CollectionMapper mapper;
    private final CollectionSupport support;
    private final CollectionAccessPolicy access;

    public CollectionBatchService(CollectionMapper mapper, CollectionSupport support,
                                  CollectionAccessPolicy access) {
        this.mapper = mapper;
        this.support = support;
        this.access = access;
    }

    @Transactional
    public Map<String, Object> create(Long planId, String key, CollectionBatchCreateDTO request) {
        return support.idempotent("CREATE_COLLECTION_BATCH:" + planId, key, request,
                () -> createBatch(planId, request, key.trim()));
    }

    public Map<String, Object> list(Long planId, String status, int page, int pageSize) {
        validatePage(page, pageSize);
        if (status != null && !STATUSES.contains(status)) {
            throw CollectionApiException.invalid();
        }
        CollectionPlanRow plan = requirePlan(planId);
        access.requirePlanRead(plan);
        long total = mapper.countBatches(planId, status);
        List<Map<String, Object>> list = total == 0 ? List.of() : mapper.selectBatches(planId,
                support.requiredUserId(), status, (page - 1) * pageSize, pageSize).stream()
                .map(this::view).toList();
        return page(total, page, pageSize, list);
    }

    public Map<String, Object> detail(Long id) {
        CollectionBatchRow batch = requireBatch(id);
        Map<String, Object> result = view(batch);
        CollectionPlanRow plan = mapper.selectPlan(batch.getPlanId(), support.requiredUserId());
        result.put("plan", plan == null ? null : Map.of("id", plan.getId(), "name", plan.getName(),
                "code", plan.getPlanCode(), "ownerId", plan.getOwnerId()));

        DataImportRow dataImport = mapper.selectImportByBatch(id);
        result.put("imports", dataImport == null ? List.of() : List.of(importView(dataImport)));
        result.put("manualRecords", mapper.selectManualRecordsByBatch(id).stream().map(this::manualView).toList());

        CollectionSnapshotRow snapshot = mapper.selectSnapshotByBatch(id);
        result.put("snapshot", snapshot == null ? null : snapshotView(snapshot));

        List<CollectionRecordRow> samples = mapper.selectEffectiveRecords(id, batch.getSourceType(),
                batch.getSourceRevisionId(), null, null, 10);
        result.put("recordSamples", samples.stream().map(this::recordView).toList());
        result.put("errorSamples", samples.stream().filter(row -> "REJECTED".equals(row.getQualityStatus()))
                .map(this::errorView).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> complete(Long id, String key, CollectionVersionDTO request) {
        return support.idempotent("COMPLETE_COLLECTION_BATCH:" + id, key, request, () -> {
            CollectionBatchRow batch = requireBatch(id);
            access.requireExecute(batch);
            requireVersion(batch, request.getVersion());
            if (!"REVIEW_REQUIRED".equals(batch.getStatus())) {
                throw CollectionApiException.batchInvalidTransition();
            }
            if (!StringUtils.hasText(request.getCompletionReason())) {
                throw CollectionApiException.invalid();
            }
            Long sourceRevisionId = null;
            if ("FILE".equals(batch.getSourceType())) {
                sourceRevisionId = mapper.selectConfirmedSourceRevision(id);
                if (sourceRevisionId == null) {
                    throw CollectionApiException.batchInvalidTransition();
                }
            }
            List<Map<String, Object>> checksumRows = mapper.selectEffectiveChecksumRows(
                    id, batch.getSourceType(), sourceRevisionId);
            if (checksumRows.isEmpty()) {
                throw CollectionApiException.batchInvalidTransition();
            }
            CollectionRecordSummaryRow summary = mapper.selectEffectiveRecordSummary(
                    id, batch.getSourceType(), sourceRevisionId);
            long recordCount = value(summary.getRecordCount());
            long rejectedCount = value(summary.getRejectedCount());
            if (sourceRevisionId != null) {
                rejectedCount = value(mapper.selectImportRevision(sourceRevisionId).getRejectedCount());
            }
            String checksum = support.digestValue(checksumRows);
            LocalDateTime now = LocalDateTime.now();
            if (mapper.completeBatch(id, request.getVersion(), recordCount, rejectedCount,
                    summary.getObservedFrom(), summary.getObservedTo(), sourceRevisionId,
                    request.getCompletionReason().trim(), checksum, now) != 1) {
                throw versionConflict(id);
            }
            support.audit(key.trim(), "COMPLETE", "COLLECTION_BATCH", id, batch.getProjectId(),
                    "REVIEW_REQUIRED", "COMPLETED", request.getCompletionReason().trim());
            batch.setRecordCount(recordCount);
            batch.setRejectedCount(rejectedCount);
            batch.setChecksum(checksum);
            batch.setSourceRevisionId(sourceRevisionId);
            return action(batch, "COMPLETED", request.getVersion() + 1);
        });
    }

    @Transactional
    public Map<String, Object> cancel(Long id, String key, CollectionVersionDTO request) {
        if (!StringUtils.hasText(request.getCompletionReason())) {
            throw CollectionApiException.invalid();
        }
        return move(id, key, request, CANCELABLE, "CANCELED", "CANCEL_COLLECTION_BATCH");
    }

    @Transactional
    public Map<String, Object> archive(Long id, String key, CollectionVersionDTO request) {
        return move(id, key, request, List.of("COMPLETED"), "ARCHIVED", "ARCHIVE_COLLECTION_BATCH");
    }

    private Map<String, Object> createBatch(Long planId, CollectionBatchCreateDTO request, String requestId) {
        CollectionPlanRow plan = requirePlan(planId);
        access.requireExecute(plan);
        if (!plan.getVersion().equals(request.getPlanVersion())) {
            throw CollectionApiException.versionConflict(plan.getVersion());
        }
        if (!"READY".equals(plan.getStatus())) {
            throw CollectionApiException.planNotReady();
        }
        if (!plan.getSourceType().equals(request.getSourceType())) {
            throw CollectionApiException.invalid();
        }
        LocalDateTime now = LocalDateTime.now();
        CollectionBatchRow batch = new CollectionBatchRow();
        batch.setBatchCode("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        batch.setPlanId(planId);
        batch.setProjectId(plan.getProjectId());
        batch.setSourceType(plan.getSourceType());
        batch.setOperatorId(support.requiredUserId());
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        mapper.insertBatch(batch);
        String code = "CB-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%06d", batch.getId());
        mapper.assignBatchCode(batch.getId(), code);
        support.audit(requestId, "CREATE", "COLLECTION_BATCH", batch.getId(), plan.getProjectId(),
                null, "CREATED", null);
        batch.setBatchCode(code);
        batch.setStatus("CREATED");
        batch.setVersion(1);
        batch.setRecordCount(0L);
        batch.setRejectedCount(0L);
        return action(batch, "CREATED", 1);
    }

    private Map<String, Object> move(Long id, String key, CollectionVersionDTO request,
                                     List<String> fromStates, String to, String operation) {
        return support.idempotent(operation + ":" + id, key, request, () -> {
            CollectionBatchRow batch = requireBatch(id);
            access.requireExecute(batch);
            requireVersion(batch, request.getVersion());
            if (!fromStates.contains(batch.getStatus())) {
                if (Set.of("COMPLETED", "ARCHIVED").contains(batch.getStatus()) && !"ARCHIVED".equals(to)) {
                    throw CollectionApiException.batchImmutable();
                }
                throw CollectionApiException.batchInvalidTransition();
            }
            LocalDateTime now = LocalDateTime.now();
            if (mapper.moveBatch(id, fromStates, to, request.getVersion(),
                    trim(request.getCompletionReason()), now) != 1) {
                throw versionConflict(id);
            }
            support.audit(key.trim(), operation, "COLLECTION_BATCH", id, batch.getProjectId(),
                    batch.getStatus(), to, trim(request.getCompletionReason()));
            return action(batch, to, request.getVersion() + 1);
        });
    }

    private CollectionPlanRow requirePlan(Long id) {
        CollectionPlanRow plan = id == null ? null : mapper.selectPlan(id, support.requiredUserId());
        access.requirePlanRead(plan);
        return plan;
    }

    private CollectionBatchRow requireBatch(Long id) {
        CollectionBatchRow batch = id == null ? null : mapper.selectBatch(id, support.requiredUserId());
        access.requireBatchRead(batch);
        return batch;
    }

    private void requireVersion(CollectionBatchRow batch, Integer version) {
        if (!batch.getVersion().equals(version)) {
            throw CollectionApiException.versionConflict(batch.getVersion());
        }
    }

    private CollectionApiException versionConflict(Long id) {
        CollectionBatchRow current = mapper.selectBatch(id, support.requiredUserId());
        return CollectionApiException.versionConflict(current == null ? 1 : current.getVersion());
    }

    private Map<String, Object> view(CollectionBatchRow row) {
        Map<String, Object> result = action(row, row.getStatus(), row.getVersion());
        result.put("planId", row.getPlanId());
        result.put("planName", row.getPlanName());
        result.put("projectId", row.getProjectId());
        result.put("projectName", row.getProjectName());
        result.put("operatorId", row.getOperatorId());
        result.put("operatorName", row.getOperatorName());
        result.put("startedAt", row.getStartedAt());
        result.put("completedAt", row.getCompletedAt());
        result.put("observedFrom", row.getObservedFrom());
        result.put("observedTo", row.getObservedTo());
        result.put("completionReason", row.getCompletionReason());
        result.put("createTime", row.getCreateTime());
        result.put("updateTime", row.getUpdateTime());
        return result;
    }

    private Map<String, Object> importView(DataImportRow row) {
        DataImportRevisionRow revision = row.getCurrentRevisionId() == null ? null
                : mapper.selectImportRevision(row.getCurrentRevisionId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("batchId", row.getBatchId());
        result.put("status", row.getStatus());
        result.put("version", row.getVersion());
        result.put("importVersion", revision == null ? 0 : revision.getImportRevisionNo());
        result.put("recordCount", revision == null ? 0 : value(revision.getRecordCount()));
        result.put("rejectedCount", revision == null ? 0 : value(revision.getRejectedCount()));
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("fileName", row.getOriginalFileName());
        file.put("size", row.getFileSize());
        file.put("sha256", row.getFileSha256());
        file.put("uploadTime", row.getCreateTime());
        result.put("originalFile", file);
        return result;
    }

    private Map<String, Object> manualView(ManualRecordRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("batchId", row.getBatchId());
        result.put("metricCode", row.getMetricCode());
        result.put("valueType", row.getValueType());
        result.put("value", row.getValueText());
        result.put("unit", row.getUnit());
        result.put("observedAt", row.getObservedAt());
        result.put("method", row.getMethod());
        result.put("instrument", row.getInstrument());
        result.put("note", row.getNote());
        result.put("version", row.getVersion());
        result.put("revisionNo", row.getRevisionNo());
        result.put("previousRevisionId", row.getPreviousRevisionId());
        result.put("recordedBy", row.getRecordedBy());
        result.put("recordedByName", row.getRecordedByName());
        return result;
    }

    private Map<String, Object> snapshotView(CollectionSnapshotRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("snapshotCode", row.getSnapshotCode());
        result.put("batchId", row.getBatchId());
        result.put("snapshotType", row.getSnapshotType());
        result.put("recordCount", row.getRecordCount());
        result.put("rejectedCount", row.getRejectedCount());
        result.put("observedFrom", row.getObservedFrom());
        result.put("observedTo", row.getObservedTo());
        result.put("batchChecksum", row.getBatchChecksum());
        result.put("mappingDigest", row.getMappingDigest());
        result.put("createTime", row.getCreateTime());
        return result;
    }

    private Map<String, Object> recordView(CollectionRecordRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("metricCode", row.getMetricCode());
        result.put("value", row.getValueText());
        result.put("unit", row.getUnit());
        result.put("observedAt", row.getObservedAt());
        result.put("qualityStatus", row.getQualityStatus());
        result.put("rawRowNo", row.getRawRowNo());
        return result;
    }

    private Map<String, Object> errorView(CollectionRecordRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rawRowNo", row.getRawRowNo());
        result.put("metricCode", row.getMetricCode());
        result.put("reason", "PARSE_ERROR");
        result.put("message", "记录未通过导入校验");
        return result;
    }

    private Map<String, Object> action(CollectionBatchRow row, String status, int version) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("batchCode", row.getBatchCode());
        result.put("status", status);
        result.put("sourceType", row.getSourceType());
        result.put("version", version);
        result.put("recordCount", value(row.getRecordCount()));
        result.put("rejectedCount", value(row.getRejectedCount()));
        result.put("sourceRevisionId", row.getSourceRevisionId());
        result.put("checksum", row.getChecksum());
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
            throw CollectionApiException.invalid();
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
