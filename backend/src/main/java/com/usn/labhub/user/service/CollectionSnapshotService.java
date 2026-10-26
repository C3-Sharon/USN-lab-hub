package com.usn.labhub.user.service;

import com.usn.labhub.user.collection.CollectionAccessPolicy;
import com.usn.labhub.user.collection.CollectionApiException;
import com.usn.labhub.user.collection.CollectionSupport;
import com.usn.labhub.user.domain.dto.collection.CollectionSnapshotBindingDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionSnapshotCreateDTO;
import com.usn.labhub.user.domain.entity.collection.CollectionBatchRow;
import com.usn.labhub.user.domain.entity.collection.CollectionRecordRow;
import com.usn.labhub.user.domain.entity.collection.CollectionSnapshotRow;
import com.usn.labhub.user.domain.entity.collection.ExperimentSnapshotBindingRow;
import com.usn.labhub.user.mapper.CollectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CollectionSnapshotService {
    private final CollectionMapper mapper;
    private final CollectionSupport support;
    private final CollectionAccessPolicy access;

    public CollectionSnapshotService(CollectionMapper mapper, CollectionSupport support,
                                     CollectionAccessPolicy access) {
        this.mapper = mapper;
        this.support = support;
        this.access = access;
    }

    public Map<String, Object> records(Long batchId, String cursor, int limit) {
        if (limit < 1 || limit > 500) throw CollectionApiException.invalid();
        CollectionBatchRow batch = requireBatch(batchId);
        access.requireBatchRead(batch);
        Cursor parsed = decode(cursor);
        List<CollectionRecordRow> rows = mapper.selectEffectiveRecords(batchId, batch.getSourceType(),
                effectiveSourceRevision(batch), parsed.time(), parsed.id(), limit + 1);
        boolean more = rows.size() > limit;
        List<CollectionRecordRow> visible = more ? rows.subList(0, limit) : rows;
        String next = more && !visible.isEmpty() ? encode(visible.get(visible.size() - 1)) : null;
        return Map.of("nextCursor", next == null ? "" : next, "hasMore", more,
                "list", visible.stream().map(this::recordView).toList());
    }

    @Transactional
    public Map<String, Object> create(Long batchId, String key, CollectionSnapshotCreateDTO request) {
        return support.idempotent("CREATE_COLLECTION_SNAPSHOT:" + batchId, key, request,
                () -> createInTransaction(batchId, key.trim(), request));
    }

    @Transactional
    public Map<String, Object> bind(Long recordId, CollectionSnapshotBindingDTO request) {
        ExperimentSnapshotBindingRow binding = mapper.selectExperimentRevisionBinding(recordId,
                request.getRevisionId(), support.requiredUserId());
        if (binding == null) throw CollectionApiException.experimentDenied();
        Long current = binding.getCollectionSnapshotId();
        if (java.util.Objects.equals(current, request.getSnapshotId())) {
            return bindingResult(recordId, request.getRevisionId(), binding.getRecordVersion(),
                    binding.getRevisionVersion(), current);
        }
        if (!"EDITABLE".equals(binding.getRevisionStatus())) throw CollectionApiException.revisionImmutable();
        if (!binding.getRecordVersion().equals(request.getRecordVersion())
                || !binding.getRevisionVersion().equals(request.getRevisionVersion())) {
            throw CollectionApiException.versionConflict(binding.getRecordVersion());
        }
        if (current != null && request.getSnapshotId() != null && !Boolean.TRUE.equals(request.getReplaceExisting())) {
            throw CollectionApiException.snapshotAlreadyBound();
        }
        if (request.getSnapshotId() != null) {
            CollectionSnapshotRow snapshot = mapper.selectSnapshot(request.getSnapshotId());
            if (snapshot == null) throw CollectionApiException.snapshotNotFound();
            CollectionBatchRow batch = requireBatch(snapshot.getBatchId());
            access.requireBatchRead(batch);
            if (binding.getProjectId() != null && !binding.getProjectId().equals(batch.getProjectId())) {
                throw CollectionApiException.snapshotNotFound();
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateExperimentSnapshotBinding(recordId, request.getRevisionId(), request.getRecordVersion(),
                request.getRevisionVersion(), request.getSnapshotId(), now) != 1
                || mapper.touchExperimentRecord(recordId, request.getRecordVersion(), now) != 1) {
            throw CollectionApiException.versionConflict(request.getRecordVersion());
        }
        support.audit(UUID.randomUUID().toString(), request.getSnapshotId() == null ? "UNBIND_SNAPSHOT" : "BIND_SNAPSHOT",
                "EXPERIMENT_REVISION", request.getRevisionId(), binding.getProjectId(), null, null, null);
        return bindingResult(recordId, request.getRevisionId(), request.getRecordVersion() + 1,
                request.getRevisionVersion() + 1, request.getSnapshotId());
    }

    private Map<String, Object> createInTransaction(Long batchId, String requestId,
                                                     CollectionSnapshotCreateDTO request) {
        CollectionBatchRow batch = requireBatch(batchId);
        access.requireBatchRead(batch);
        if (!"FULL_BATCH".equals(request.getSnapshotType())) throw CollectionApiException.invalid();
        if (!"COMPLETED".equals(batch.getStatus()) && !"ARCHIVED".equals(batch.getStatus())) {
            throw CollectionApiException.batchNotCompleted();
        }
        if (!batch.getVersion().equals(request.getBatchVersion())) {
            throw CollectionApiException.versionConflict(batch.getVersion());
        }
        CollectionSnapshotRow existing = mapper.selectSnapshotByBatch(batchId);
        if (existing != null) return snapshotView(existing);
        CollectionSnapshotRow row = new CollectionSnapshotRow();
        row.setSnapshotCode("CS-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%06d", batchId));
        row.setBatchId(batchId);
        row.setSnapshotType("FULL_BATCH");
        row.setSourceRevisionId(batch.getSourceRevisionId());
        row.setRecordCount(batch.getRecordCount());
        row.setRejectedCount(batch.getRejectedCount());
        row.setObservedFrom(batch.getObservedFrom());
        row.setObservedTo(batch.getObservedTo());
        row.setBatchChecksum(batch.getChecksum());
        row.setMappingDigest("FILE".equals(batch.getSourceType())
                ? mapper.selectImportMappingDigest(batch.getSourceRevisionId()) : null);
        row.setCreatedBy(support.requiredUserId());
        row.setCreateTime(LocalDateTime.now());
        mapper.insertSnapshot(row);
        if ("MANUAL".equals(batch.getSourceType())) mapper.insertSnapshotManualRevisions(row.getId(), batchId);
        support.audit(requestId, "CREATE", "COLLECTION_SNAPSHOT", row.getId(), batch.getProjectId(),
                null, "FULL_BATCH", null);
        return snapshotView(row);
    }

    private Long effectiveSourceRevision(CollectionBatchRow batch) {
        if (!"FILE".equals(batch.getSourceType())) return null;
        return batch.getSourceRevisionId() != null ? batch.getSourceRevisionId()
                : mapper.selectConfirmedSourceRevision(batch.getId());
    }

    private CollectionBatchRow requireBatch(Long id) {
        CollectionBatchRow batch = id == null ? null : mapper.selectBatch(id, support.requiredUserId());
        access.requireBatchRead(batch);
        return batch;
    }

    private Map<String, Object> snapshotView(CollectionSnapshotRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("snapshotCode", row.getSnapshotCode());
        result.put("batchId", row.getBatchId());
        result.put("snapshotType", row.getSnapshotType());
        result.put("sourceRevisionId", row.getSourceRevisionId());
        result.put("recordCount", row.getRecordCount());
        result.put("rejectedCount", row.getRejectedCount());
        result.put("observedFrom", row.getObservedFrom());
        result.put("observedTo", row.getObservedTo());
        result.put("batchChecksum", row.getBatchChecksum());
        result.put("mappingDigest", row.getMappingDigest());
        result.put("createdBy", row.getCreatedBy());
        result.put("createTime", row.getCreateTime());
        return result;
    }

    private Map<String, Object> recordView(CollectionRecordRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("sourceType", row.getSourceType());
        result.put("metricCode", row.getMetricCode());
        result.put("valueType", row.getValueType());
        result.put("value", row.getValueText());
        result.put("unit", row.getUnit());
        result.put("observedAt", row.getObservedAt());
        result.put("recordedBy", row.getRecordedBy());
        result.put("method", row.getMethod());
        result.put("instrument", row.getInstrument());
        result.put("rawRowNo", row.getRawRowNo());
        result.put("qualityStatus", row.getQualityStatus());
        result.put("parserVersion", row.getParserVersion());
        return result;
    }

    private Map<String, Object> bindingResult(Long recordId, Long revisionId, int recordVersion,
                                              int revisionVersion, Long snapshotId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", recordId);
        result.put("recordVersion", recordVersion);
        result.put("revisionId", revisionId);
        result.put("revisionVersion", revisionVersion);
        result.put("collectionSnapshotId", snapshotId);
        return result;
    }

    private String encode(CollectionRecordRow row) {
        String raw = row.getObservedAt() + "|" + row.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return new Cursor(null, null);
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.lastIndexOf('|');
            return new Cursor(LocalDateTime.parse(raw.substring(0, split)), Long.valueOf(raw.substring(split + 1)));
        } catch (IllegalArgumentException | DateTimeParseException | IndexOutOfBoundsException exception) {
            throw CollectionApiException.invalid();
        }
    }

    private record Cursor(LocalDateTime time, Long id) { }
}
