package com.usn.labhub.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.usn.labhub.user.collection.CollectionAccessPolicy;
import com.usn.labhub.user.collection.CollectionApiException;
import com.usn.labhub.user.collection.CollectionSupport;
import com.usn.labhub.user.domain.dto.collection.ManualRecordCreateDTO;
import com.usn.labhub.user.domain.dto.collection.ManualRecordUpdateDTO;
import com.usn.labhub.user.domain.entity.collection.CollectionBatchRow;
import com.usn.labhub.user.domain.entity.collection.CollectionPlanRow;
import com.usn.labhub.user.domain.entity.collection.CollectionRecordRow;
import com.usn.labhub.user.domain.entity.collection.ManualRecordRevisionRow;
import com.usn.labhub.user.domain.entity.collection.ManualRecordRow;
import com.usn.labhub.user.mapper.CollectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ManualCollectionService {
    private static final Set<String> VALUE_TYPES = Set.of("DECIMAL", "INTEGER", "BOOLEAN", "STRING");
    private final CollectionMapper mapper;
    private final CollectionSupport support;
    private final CollectionAccessPolicy access;

    public ManualCollectionService(CollectionMapper mapper, CollectionSupport support,
                                   CollectionAccessPolicy access) {
        this.mapper = mapper;
        this.support = support;
        this.access = access;
    }

    @Transactional
    public Map<String, Object> create(Long batchId, String key, ManualRecordCreateDTO request) {
        return support.idempotent("CREATE_MANUAL_RECORD:" + batchId, key, request,
                () -> createInTransaction(batchId, key.trim(), request));
    }

    @Transactional
    public Map<String, Object> update(Long recordId, ManualRecordUpdateDTO request) {
        ManualRecordRow record = requireRecord(recordId);
        mapper.lockBatch(record.getBatchId());
        CollectionBatchRow batch = requireBatch(record.getBatchId());
        access.requireExecute(batch);
        requireManualMutable(batch);
        if (!record.getVersion().equals(request.getVersion())
                || !batch.getVersion().equals(request.getBatchVersion())) {
            throw CollectionApiException.versionConflict(record.getVersion());
        }
        validateValue(record.getMetricCode(), request.getValueType(), request.getValue(), request.getUnit(), batch);
        List<ManualRecordRevisionRow> history = mapper.selectManualRevisions(recordId);
        ManualRecordRevisionRow revision = revision(recordId, history.size() + 1, request.getValueType(),
                request.getValue(), request.getUnit(), request.getObservedAt().toLocalDateTime(),
                request.getMethod(), request.getInstrument(), request.getNote(), request.getChangeReason(),
                record.getCurrentRevisionId());
        mapper.insertManualRevision(revision);
        if (mapper.setCurrentManualRevision(recordId, request.getVersion(), revision.getId(), LocalDateTime.now()) != 1
                || mapper.markBatchReviewRequired(batch.getId(), request.getBatchVersion(), LocalDateTime.now()) != 1) {
            throw CollectionApiException.versionConflict(record.getVersion());
        }
        insertNormalized(batch, record, revision);
        support.audit(java.util.UUID.randomUUID().toString(), "CORRECT", "MANUAL_RECORD", recordId,
                batch.getProjectId(), null, null, request.getChangeReason().trim());
        return action(record, revision, request.getVersion() + 1, request.getBatchVersion() + 1);
    }

    public Map<String, Object> revisions(Long recordId) {
        ManualRecordRow record = requireRecord(recordId);
        CollectionBatchRow batch = requireBatch(record.getBatchId());
        access.requireBatchRead(batch);
        return Map.of("id", recordId, "version", record.getVersion(), "revisions",
                mapper.selectManualRevisions(recordId).stream().map(this::revisionView).toList());
    }

    private Map<String, Object> createInTransaction(Long batchId, String requestId,
                                                     ManualRecordCreateDTO request) {
        mapper.lockBatch(batchId);
        CollectionBatchRow batch = requireBatch(batchId);
        access.requireExecute(batch);
        requireManualMutable(batch);
        validateValue(request.getMetricCode(), request.getValueType(), request.getValue(), request.getUnit(), batch);
        LocalDateTime now = LocalDateTime.now();
        ManualRecordRow record = new ManualRecordRow();
        record.setBatchId(batchId);
        record.setMetricCode(request.getMetricCode().trim());
        record.setCreatedBy(support.requiredUserId());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mapper.insertManualRecord(record);
        ManualRecordRevisionRow revision = revision(record.getId(), 1, request.getValueType(), request.getValue(),
                request.getUnit(), request.getObservedAt().toLocalDateTime(), request.getMethod(),
                request.getInstrument(), request.getNote(), null, null);
        mapper.insertManualRevision(revision);
        if (mapper.setInitialManualRevision(record.getId(), revision.getId(), now) != 1
                || mapper.markBatchReviewRequired(batchId, batch.getVersion(), now) != 1) {
            throw CollectionApiException.importProcessingFailed();
        }
        insertNormalized(batch, record, revision);
        support.audit(requestId, "CREATE", "MANUAL_RECORD", record.getId(), batch.getProjectId(),
                null, null, null);
        record.setVersion(1);
        return action(record, revision, 1, batch.getVersion() + 1);
    }

    private void insertNormalized(CollectionBatchRow batch, ManualRecordRow record,
                                  ManualRecordRevisionRow revision) {
        CollectionRecordRow normalized = new CollectionRecordRow();
        normalized.setBatchId(batch.getId());
        normalized.setManualRecordRevisionId(revision.getId());
        normalized.setSourceType("MANUAL");
        normalized.setMetricCode(record.getMetricCode());
        normalized.setValueType(revision.getValueType());
        normalized.setValueText(revision.getValueText());
        normalized.setUnit(revision.getUnit());
        normalized.setObservedAt(revision.getObservedAt());
        normalized.setRecordedBy(support.requiredUserId());
        normalized.setMethod(revision.getMethod());
        normalized.setInstrument(revision.getInstrument());
        normalized.setQualityStatus("GOOD");
        normalized.setCreateTime(LocalDateTime.now());
        mapper.insertCollectionRecord(normalized);
    }

    private void validateValue(String metricCode, String valueType, JsonNode value, String unit,
                               CollectionBatchRow batch) {
        if (!"MANUAL".equals(batch.getSourceType()) || !VALUE_TYPES.contains(valueType)
                || !StringUtils.hasText(metricCode) || value == null || value.isNull()
                || !StringUtils.hasText(unit)) {
            throw CollectionApiException.manualInvalid();
        }
        CollectionPlanRow plan = mapper.selectPlan(batch.getPlanId(), support.requiredUserId());
        List<Map<String, Object>> definitions = support.readJson(plan.getMetricDefinitionsJson(), new TypeReference<>() { });
        Map<String, Object> definition = definitions.stream()
                .filter(item -> metricCode.equals(String.valueOf(item.get("code"))))
                .findFirst().orElseThrow(CollectionApiException::manualInvalid);
        if (!valueType.equals(String.valueOf(definition.get("valueType")))
                || !unit.equals(String.valueOf(definition.get("unit")))) {
            throw CollectionApiException.manualInvalid();
        }
        try {
            switch (valueType) {
                case "DECIMAL" -> new BigDecimal(value.asText());
                case "INTEGER" -> Long.parseLong(value.asText());
                case "BOOLEAN" -> {
                    if (!value.isBoolean() && !Set.of("true", "false").contains(value.asText().toLowerCase())) {
                        throw CollectionApiException.manualInvalid();
                    }
                }
                case "STRING" -> {
                    if (!value.isTextual() || value.asText().length() > 2000) throw CollectionApiException.manualInvalid();
                }
                default -> throw CollectionApiException.manualInvalid();
            }
        } catch (NumberFormatException exception) {
            throw CollectionApiException.manualInvalid();
        }
    }

    private ManualRecordRevisionRow revision(Long recordId, int no, String type, JsonNode value,
                                             String unit, LocalDateTime observedAt, String method,
                                             String instrument, String note, String reason, Long previousId) {
        ManualRecordRevisionRow row = new ManualRecordRevisionRow();
        row.setManualRecordId(recordId);
        row.setRevisionNo(no);
        row.setValueType(type);
        row.setValueText(value.isTextual() ? value.asText() : value.toString());
        row.setUnit(unit.trim());
        row.setObservedAt(observedAt);
        row.setMethod(method.trim());
        row.setInstrument(instrument.trim());
        row.setNote(trim(note));
        row.setChangeReason(trim(reason));
        row.setPreviousRevisionId(previousId);
        row.setCreatedBy(support.requiredUserId());
        row.setCreateTime(LocalDateTime.now());
        return row;
    }

    private ManualRecordRow requireRecord(Long id) {
        ManualRecordRow row = id == null ? null : mapper.selectManualRecord(id);
        if (row == null) throw CollectionApiException.manualRecordNotFound();
        return row;
    }

    private CollectionBatchRow requireBatch(Long id) {
        CollectionBatchRow row = mapper.selectBatch(id, support.requiredUserId());
        access.requireBatchRead(row);
        return row;
    }

    private void requireManualMutable(CollectionBatchRow batch) {
        if (Set.of("COMPLETED", "ARCHIVED").contains(batch.getStatus())) throw CollectionApiException.batchImmutable();
        if (!"MANUAL".equals(batch.getSourceType())
                || !Set.of("CREATED", "REVIEW_REQUIRED").contains(batch.getStatus())) {
            throw CollectionApiException.batchInvalidTransition();
        }
    }

    private Map<String, Object> action(ManualRecordRow record, ManualRecordRevisionRow revision,
                                       int version, int batchVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("version", version);
        result.put("revisionId", revision.getId());
        result.put("revisionNo", revision.getRevisionNo());
        result.put("previousRevisionId", revision.getPreviousRevisionId());
        result.put("batchVersion", batchVersion);
        return result;
    }

    private Map<String, Object> revisionView(ManualRecordRevisionRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("revisionNo", row.getRevisionNo());
        result.put("valueType", row.getValueType());
        result.put("value", row.getValueText());
        result.put("unit", row.getUnit());
        result.put("observedAt", row.getObservedAt());
        result.put("method", row.getMethod());
        result.put("instrument", row.getInstrument());
        result.put("note", row.getNote());
        result.put("changeReason", row.getChangeReason());
        result.put("previousRevisionId", row.getPreviousRevisionId());
        result.put("createdBy", row.getCreatedBy());
        result.put("createTime", row.getCreateTime());
        return result;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
