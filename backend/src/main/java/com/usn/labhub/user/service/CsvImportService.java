package com.usn.labhub.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.collection.CollectionAccessPolicy;
import com.usn.labhub.user.collection.CollectionApiException;
import com.usn.labhub.user.collection.CollectionSupport;
import com.usn.labhub.user.collection.CommittedCollectionFile;
import com.usn.labhub.user.collection.LocalImmutableFileStorage;
import com.usn.labhub.user.collection.StagedCollectionFile;
import com.usn.labhub.user.domain.dto.collection.DataImportConfirmDTO;
import com.usn.labhub.user.domain.dto.collection.DataImportMappingDTO;
import com.usn.labhub.user.domain.entity.collection.CollectionBatchRow;
import com.usn.labhub.user.domain.entity.collection.CollectionPlanRow;
import com.usn.labhub.user.domain.entity.collection.CollectionRecordRow;
import com.usn.labhub.user.domain.entity.collection.DataImportRevisionRow;
import com.usn.labhub.user.domain.entity.collection.DataImportRow;
import com.usn.labhub.user.mapper.CollectionMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CsvImportService {
    private static final long MAX_ROWS = 100_000;
    private static final int MAX_COLUMNS = 100;
    private static final Set<String> VALUE_TYPES = Set.of("DECIMAL", "INTEGER", "BOOLEAN", "STRING");
    private final CollectionMapper mapper;
    private final CollectionSupport support;
    private final CollectionAccessPolicy access;
    private final LocalImmutableFileStorage storage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public CsvImportService(CollectionMapper mapper, CollectionSupport support, CollectionAccessPolicy access,
                            LocalImmutableFileStorage storage, ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.support = support;
        this.access = access;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> upload(Long batchId, String key, MultipartFile file) {
        StagedCollectionFile staged = storage.stage(file);
        CommittedCollectionFile[] committed = new CommittedCollectionFile[1];
        try {
            Map<String, Object> request = Map.of("batchId", batchId, "sha256", staged.sha256());
            return transactions.execute(status -> support.idempotent("UPLOAD_COLLECTION_FILE:" + batchId,
                    key, request, () -> uploadInTransaction(batchId, key.trim(), staged, committed)));
        } catch (RuntimeException exception) {
            storage.rollback(committed[0]);
            throw exception;
        } finally {
            storage.discard(staged);
        }
    }

    public Map<String, Object> preview(Long importId) {
        DataImportRow dataImport = requireImport(importId);
        CollectionBatchRow batch = requireBatch(dataImport.getBatchId());
        access.requireBatchRead(batch);
        CsvContent content = readCsv(dataImport, null, 50, false);
        Map<String, Object> result = importBase(dataImport, batch.getVersion());
        result.put("headers", content.headers());
        result.put("rows", content.preview());
        result.put("warnings", content.warnings());
        result.put("truncated", content.totalRows() > 50);
        return result;
    }

    public Map<String, Object> detail(Long importId) {
        DataImportRow dataImport = requireImport(importId);
        CollectionBatchRow batch = requireBatch(dataImport.getBatchId());
        access.requireBatchRead(batch);
        Map<String, Object> result = importBase(dataImport, batch.getVersion());
        DataImportRevisionRow current = dataImport.getCurrentRevisionId() == null ? null
                : mapper.selectImportRevision(dataImport.getCurrentRevisionId());
        result.put("currentRevision", revisionView(current));
        result.put("revisions", mapper.selectImportRevisions(importId).stream().map(this::revisionView).toList());
        return result;
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> saveMapping(Long importId, DataImportMappingDTO request) {
        DataImportRow dataImport = requireImport(importId);
        CollectionBatchRow batch = requireBatch(dataImport.getBatchId());
        access.requireExecute(batch);
        requireMutable(batch);
        if (!dataImport.getVersion().equals(request.getVersion())
                || !Integer.valueOf(mapper.selectImportRevisions(importId).size()).equals(request.getImportVersion())) {
            throw CollectionApiException.versionConflict(dataImport.getVersion());
        }
        validateMapping(dataImport, batch, request.getMapping());
        String json = support.writeJson(request.getMapping());
        String digest = support.digestValue(request.getMapping());
        List<DataImportRevisionRow> revisions = mapper.selectImportRevisions(importId);
        if (!revisions.isEmpty() && digest.equals(revisions.get(0).getMappingDigest())) {
            return mappingResult(dataImport, batch.getVersion(), revisions.get(0));
        }
        DataImportRevisionRow revision = new DataImportRevisionRow();
        revision.setDataImportId(importId);
        revision.setImportRevisionNo(revisions.size() + 1);
        revision.setMappingJson(json);
        revision.setMappingDigest(digest);
        revision.setPreviousRevisionId(dataImport.getCurrentRevisionId());
        revision.setCreatedBy(support.requiredUserId());
        revision.setCreateTime(LocalDateTime.now());
        mapper.insertImportRevision(revision);
        if (mapper.setCurrentImportRevision(importId, request.getVersion(), revision.getId(), LocalDateTime.now()) != 1) {
            throw CollectionApiException.versionConflict(dataImport.getVersion());
        }
        support.audit(java.util.UUID.randomUUID().toString(), "SAVE_MAPPING", "DATA_IMPORT", importId,
                batch.getProjectId(), dataImport.getStatus(), "MAPPING_REQUIRED", null);
        dataImport.setVersion(request.getVersion() + 1);
        dataImport.setStatus("MAPPING_REQUIRED");
        return mappingResult(dataImport, batch.getVersion(), revision);
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> confirm(Long importId, String key, DataImportConfirmDTO request) {
        return support.idempotent("CONFIRM_DATA_IMPORT:" + importId, key, request,
                () -> confirmInTransaction(importId, key.trim(), request));
    }

    private Map<String, Object> uploadInTransaction(Long batchId, String requestId, StagedCollectionFile staged,
                                                     CommittedCollectionFile[] committed) {
        mapper.lockBatch(batchId);
        CollectionBatchRow batch = requireBatch(batchId);
        access.requireExecute(batch);
        requireMutable(batch);
        if (!"FILE".equals(batch.getSourceType()) || !Set.of("CREATED", "PROCESSING").contains(batch.getStatus())) {
            throw CollectionApiException.batchInvalidTransition();
        }
        DataImportRow existing = mapper.selectImportByBatch(batchId);
        if (existing != null) {
            if (!existing.getFileSha256().equals(staged.sha256())) {
                throw CollectionApiException.invalid();
            }
            return uploadResult(existing, batch.getVersion());
        }
        committed[0] = storage.commit(batchId, staged);
        LocalDateTime now = LocalDateTime.now();
        DataImportRow row = new DataImportRow();
        row.setBatchId(batchId);
        row.setObjectKey(committed[0].objectKey());
        row.setOriginalFileName(staged.originalFileName());
        row.setMimeType(staged.mimeType());
        row.setFileSize(staged.size());
        row.setFileSha256(staged.sha256());
        row.setUploadedBy(support.requiredUserId());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        mapper.insertImport(row);
        int batchVersion = batch.getVersion();
        if ("CREATED".equals(batch.getStatus())) {
            if (mapper.markBatchProcessing(batchId, batchVersion, now) != 1) {
                throw CollectionApiException.versionConflict(batchVersion);
            }
            batchVersion++;
        }
        row.setStatus("UPLOADED");
        row.setVersion(1);
        support.audit(requestId, "UPLOAD", "DATA_IMPORT", row.getId(), batch.getProjectId(),
                null, "UPLOADED", null);
        return uploadResult(row, batchVersion);
    }

    private Map<String, Object> confirmInTransaction(Long importId, String requestId,
                                                      DataImportConfirmDTO request) {
        DataImportRow dataImport = requireImport(importId);
        CollectionBatchRow batch = requireBatch(dataImport.getBatchId());
        access.requireExecute(batch);
        requireMutable(batch);
        if (!dataImport.getVersion().equals(request.getVersion())) {
            throw CollectionApiException.versionConflict(dataImport.getVersion());
        }
        if (!batch.getVersion().equals(request.getBatchVersion())) {
            throw CollectionApiException.versionConflict(batch.getVersion());
        }
        DataImportRevisionRow revision = dataImport.getCurrentRevisionId() == null ? null
                : mapper.selectImportRevision(dataImport.getCurrentRevisionId());
        if (revision == null || !revision.getImportRevisionNo().equals(request.getImportVersion())) {
            throw CollectionApiException.mappingInvalid();
        }
        if ("CONFIRMED".equals(revision.getStatus())) {
            throw CollectionApiException.importAlreadyConfirmed();
        }
        DataImportMappingDTO.Mapping mapping = readMapping(revision.getMappingJson());
        CsvContent content = readCsv(dataImport, mapping, (int) MAX_ROWS, true);
        long good = 0;
        long rejected = content.rejected();
        LocalDateTime now = LocalDateTime.now();
        for (ParsedValue value : content.values()) {
            CollectionRecordRow row = new CollectionRecordRow();
            row.setBatchId(batch.getId());
            row.setDataImportRevisionId(revision.getId());
            row.setSourceType("FILE");
            row.setMetricCode(value.mapping().getMetricCode());
            row.setValueType(value.mapping().getValueType());
            row.setValueText(value.value());
            row.setUnit(value.mapping().getTargetUnit());
            row.setObservedAt(value.observedAt());
            row.setRecordedBy(support.requiredUserId());
            row.setMethod("FILE_IMPORT");
            row.setRawRowNo(value.rowNo());
            row.setQualityStatus(value.good() ? "GOOD" : "REJECTED");
            row.setParserVersion("CSV-1");
            row.setCreateTime(now);
            mapper.insertCollectionRecord(row);
            if (value.good()) {
                good++;
            } else {
                rejected++;
            }
        }
        mapper.supersedeConfirmedImportRevisions(importId, revision.getId());
        if (mapper.confirmImportRevision(revision.getId(), support.requiredUserId(), good, rejected, now) != 1
                || mapper.confirmImport(importId, request.getVersion(), revision.getId(), now) != 1
                || mapper.markBatchReviewRequired(batch.getId(), request.getBatchVersion(), now) != 1) {
            throw CollectionApiException.importProcessingFailed();
        }
        support.audit(requestId, "CONFIRM", "DATA_IMPORT", importId, batch.getProjectId(),
                dataImport.getStatus(), "CONFIRMED", null);
        dataImport.setStatus("CONFIRMED");
        dataImport.setVersion(request.getVersion() + 1);
        revision.setStatus("CONFIRMED");
        revision.setRecordCount(good);
        revision.setRejectedCount(rejected);
        Map<String, Object> result = mappingResult(dataImport, request.getBatchVersion() + 1, revision);
        result.put("recordCount", good);
        result.put("rejectedCount", rejected);
        return result;
    }

    private void validateMapping(DataImportRow dataImport, CollectionBatchRow batch,
                                 DataImportMappingDTO.Mapping mapping) {
        if (mapping == null || !",".equals(mapping.getDelimiter()) || !"UTF-8".equalsIgnoreCase(mapping.getEncoding())
                || !Integer.valueOf(1).equals(mapping.getHeaderRow()) || mapping.getTimeMapping() == null
                || !StringUtils.hasText(mapping.getTimeMapping().getSourceColumn())
                || !StringUtils.hasText(mapping.getTimeMapping().getFormat())
                || !StringUtils.hasText(mapping.getTimeMapping().getTimezone())
                || mapping.getColumns() == null || mapping.getColumns().isEmpty()
                || mapping.getColumns().size() > MAX_COLUMNS) {
            throw CollectionApiException.mappingInvalid();
        }
        CsvContent csv = readCsv(dataImport, null, 1, false);
        Set<String> headers = new LinkedHashSet<>(csv.headers());
        if (!headers.contains(mapping.getTimeMapping().getSourceColumn())) {
            throw CollectionApiException.mappingInvalid();
        }
        try {
            ZoneId.of(mapping.getTimeMapping().getTimezone());
            DateTimeFormatter.ofPattern(mapping.getTimeMapping().getFormat());
        } catch (RuntimeException exception) {
            throw CollectionApiException.mappingInvalid();
        }
        CollectionPlanRow plan = mapper.selectPlan(batch.getPlanId(), support.requiredUserId());
        List<Map<String, Object>> definitions = support.readJson(plan.getMetricDefinitionsJson(), new TypeReference<>() { });
        Map<String, Map<String, Object>> metrics = new LinkedHashMap<>();
        definitions.forEach(item -> metrics.put(String.valueOf(item.get("code")), item));
        Set<String> usedSources = new LinkedHashSet<>();
        for (DataImportMappingDTO.ColumnMapping column : mapping.getColumns()) {
            Map<String, Object> metric = metrics.get(column.getMetricCode());
            if (!StringUtils.hasText(column.getSourceColumn()) || !headers.contains(column.getSourceColumn())
                    || !usedSources.add(column.getSourceColumn()) || metric == null
                    || !VALUE_TYPES.contains(column.getValueType())
                    || !column.getValueType().equals(String.valueOf(metric.get("valueType")))
                    || !StringUtils.hasText(column.getSourceUnit()) || !StringUtils.hasText(column.getTargetUnit())
                    || !column.getSourceUnit().equals(column.getTargetUnit())
                    || !column.getTargetUnit().equals(String.valueOf(metric.get("unit")))) {
                throw CollectionApiException.mappingInvalid();
            }
        }
    }

    private CsvContent readCsv(DataImportRow dataImport, DataImportMappingDTO.Mapping mapping,
                               int rowLimit, boolean parseValues) {
        try (Reader reader = Files.newBufferedReader(storage.resolve(dataImport.getObjectKey()), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(false).build().parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty() || headers.size() > MAX_COLUMNS || new LinkedHashSet<>(headers).size() != headers.size()) {
                throw CollectionApiException.fileTooLarge();
            }
            List<Map<String, String>> preview = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<ParsedValue> values = new ArrayList<>();
            long rows = 0;
            long rejected = 0;
            for (CSVRecord record : parser) {
                rows++;
                if (rows > MAX_ROWS) {
                    throw CollectionApiException.fileTooLarge();
                }
                if (preview.size() < Math.min(50, rowLimit)) {
                    preview.add(new LinkedHashMap<>(record.toMap()));
                }
                if (parseValues) {
                    LocalDateTime observedAt;
                    try {
                        observedAt = parseObservedAt(record.get(mapping.getTimeMapping().getSourceColumn()),
                                mapping.getTimeMapping());
                    } catch (RuntimeException exception) {
                        rejected += mapping.getColumns().size();
                        if (warnings.size() < 50) warnings.add("INVALID_TIME@" + record.getRecordNumber());
                        continue;
                    }
                    for (DataImportMappingDTO.ColumnMapping column : mapping.getColumns()) {
                        String raw = record.isMapped(column.getSourceColumn()) ? record.get(column.getSourceColumn()) : null;
                        boolean good = validValue(raw, column.getValueType());
                        if (!good && warnings.size() < 50) {
                            warnings.add((StringUtils.hasText(raw) ? "PARSE_ERROR@" : "MISSING_VALUE@")
                                    + record.getRecordNumber() + ":" + column.getSourceColumn());
                        }
                        values.add(new ParsedValue(column, raw == null ? "" : raw.trim(), observedAt,
                                record.getRecordNumber() + 1, good));
                    }
                }
                if (!parseValues && rows >= rowLimit + 1L) break;
            }
            return new CsvContent(headers, preview, warnings, values, rows, rejected);
        } catch (CollectionApiException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw CollectionApiException.importProcessingFailed();
        }
    }

    private LocalDateTime parseObservedAt(String value, DataImportMappingDTO.TimeMapping mapping) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(mapping.getFormat());
        try {
            return OffsetDateTime.parse(value, formatter).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value, formatter).atZone(ZoneId.of(mapping.getTimezone())).toLocalDateTime();
        }
    }

    private boolean validValue(String raw, String type) {
        if (!StringUtils.hasText(raw)) return false;
        try {
            switch (type) {
                case "DECIMAL" -> new BigDecimal(raw.trim());
                case "INTEGER" -> Long.parseLong(raw.trim());
                case "BOOLEAN" -> {
                    if (!Set.of("true", "false").contains(raw.trim().toLowerCase())) return false;
                }
                case "STRING" -> { return raw.length() <= 2000; }
                default -> { return false; }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private DataImportMappingDTO.Mapping readMapping(String json) {
        try {
            return objectMapper.readValue(json, DataImportMappingDTO.Mapping.class);
        } catch (IOException exception) {
            throw CollectionApiException.importProcessingFailed();
        }
    }

    private DataImportRow requireImport(Long id) {
        DataImportRow row = id == null ? null : mapper.selectImport(id);
        if (row == null) throw CollectionApiException.dataImportNotFound();
        return row;
    }

    private CollectionBatchRow requireBatch(Long id) {
        CollectionBatchRow row = mapper.selectBatch(id, support.requiredUserId());
        access.requireBatchRead(row);
        return row;
    }

    private void requireMutable(CollectionBatchRow batch) {
        if (Set.of("COMPLETED", "ARCHIVED").contains(batch.getStatus())) throw CollectionApiException.batchImmutable();
    }

    private Map<String, Object> uploadResult(DataImportRow row, int batchVersion) {
        Map<String, Object> result = importBase(row, batchVersion);
        result.put("importVersion", row.getCurrentRevisionId() == null ? 0 : mapper.selectImportRevisions(row.getId()).size());
        result.put("originalFile", Map.of("fileName", row.getOriginalFileName(), "size", row.getFileSize(),
                "sha256", row.getFileSha256()));
        return result;
    }

    private Map<String, Object> mappingResult(DataImportRow row, int batchVersion, DataImportRevisionRow revision) {
        Map<String, Object> result = importBase(row, batchVersion);
        result.put("importVersion", revision.getImportRevisionNo());
        result.put("currentRevision", revisionView(revision));
        return result;
    }

    private Map<String, Object> importBase(DataImportRow row, int batchVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("batchId", row.getBatchId());
        result.put("status", row.getStatus());
        result.put("version", row.getVersion());
        result.put("batchVersion", batchVersion);
        result.put("originalFile", Map.of("fileName", row.getOriginalFileName(), "size", row.getFileSize(),
                "sha256", row.getFileSha256()));
        result.put("createTime", row.getCreateTime());
        result.put("updateTime", row.getUpdateTime());
        return result;
    }

    private Map<String, Object> revisionView(DataImportRevisionRow row) {
        if (row == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("importRevisionNo", row.getImportRevisionNo());
        result.put("status", row.getStatus());
        result.put("mappingDigest", row.getMappingDigest());
        result.put("recordCount", row.getRecordCount());
        result.put("rejectedCount", row.getRejectedCount());
        result.put("createTime", row.getCreateTime());
        result.put("confirmedAt", row.getConfirmedAt());
        return result;
    }

    private record CsvContent(List<String> headers, List<Map<String, String>> preview, List<String> warnings,
                              List<ParsedValue> values, long totalRows, long rejected) { }
    private record ParsedValue(DataImportMappingDTO.ColumnMapping mapping, String value,
                               LocalDateTime observedAt, long rowNo, boolean good) { }
}
