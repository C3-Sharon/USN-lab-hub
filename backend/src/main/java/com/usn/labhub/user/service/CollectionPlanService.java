package com.usn.labhub.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.usn.labhub.user.collection.CollectionAccessPolicy;
import com.usn.labhub.user.collection.CollectionApiException;
import com.usn.labhub.user.collection.CollectionSupport;
import com.usn.labhub.user.domain.dto.collection.CollectionPlanCreateDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionPlanUpdateDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionVersionDTO;
import com.usn.labhub.user.domain.dto.collection.MetricDefinitionDTO;
import com.usn.labhub.user.domain.entity.collection.CollectionPlanRow;
import com.usn.labhub.user.mapper.CollectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CollectionPlanService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "READY", "ARCHIVED", "CANCELED");
    private static final Set<String> SOURCES = Set.of("FILE", "MANUAL");
    private static final Set<String> OWNER_ROLES = Set.of("OWNER", "MAINTAINER", "MEMBER");
    private final CollectionMapper mapper;
    private final CollectionSupport support;
    private final CollectionAccessPolicy access;

    public CollectionPlanService(CollectionMapper mapper, CollectionSupport support,
                                 CollectionAccessPolicy access) {
        this.mapper = mapper;
        this.support = support;
        this.access = access;
    }

    @Transactional
    public Map<String, Object> create(Long projectId, String key, CollectionPlanCreateDTO request) {
        return support.idempotent("CREATE_COLLECTION_PLAN:" + projectId, key, request,
                () -> createPlan(projectId, request, key.trim()));
    }

    public Map<String, Object> list(Long projectId, String status, String sourceType, int page, int pageSize) {
        validatePage(page, pageSize);
        if (status != null && !STATUSES.contains(status) || sourceType != null && !SOURCES.contains(sourceType)) {
            throw CollectionApiException.invalid();
        }
        Long userId = support.requiredUserId();
        CollectionPlanRow project = mapper.selectProjectContext(projectId, userId);
        access.requireProjectRead(project);
        long total = mapper.countPlans(projectId, status, sourceType);
        List<Map<String, Object>> list = total == 0 ? List.of() : mapper.selectPlans(projectId, userId,
                status, sourceType, (page - 1) * pageSize, pageSize).stream().map(this::view).toList();
        return page(total, page, pageSize, list);
    }

    public Map<String, Object> detail(Long id) {
        return view(requireVisible(id));
    }

    @Transactional
    public Map<String, Object> update(Long id, CollectionPlanUpdateDTO request) {
        CollectionPlanRow plan = requireVisible(id);
        access.requireManage(plan);
        if (!"DRAFT".equals(plan.getStatus())) {
            throw CollectionApiException.invalid();
        }
        requireVersion(plan, request.getVersion());
        requireName(request.getName());
        validateMetrics(request.getMetricDefinitions());
        validateOwnerAndLink(plan.getProjectId(), request.getOwnerId(), request.getLinkedExperimentRecordId());
        plan.setName(request.getName().trim());
        plan.setPurpose(request.getPurpose().trim());
        plan.setOwnerId(request.getOwnerId());
        plan.setMetricDefinitionsJson(support.writeJson(request.getMetricDefinitions()));
        plan.setLinkedExperimentRecordId(request.getLinkedExperimentRecordId());
        plan.setRetentionNote(trim(request.getRetentionNote()));
        plan.setVersion(request.getVersion());
        plan.setUpdateTime(LocalDateTime.now());
        if (mapper.updateDraftPlan(plan) != 1) {
            throw versionConflict(id);
        }
        support.audit(UUID.randomUUID().toString(), "UPDATE", "COLLECTION_PLAN", id, plan.getProjectId(),
                "DRAFT", "DRAFT", null);
        return action(plan, "DRAFT", request.getVersion() + 1);
    }

    @Transactional
    public Map<String, Object> ready(Long id, String key, CollectionVersionDTO request) {
        return stateAction(id, key, request, "DRAFT", "READY", "READY_COLLECTION_PLAN");
    }

    @Transactional
    public Map<String, Object> archive(Long id, String key, CollectionVersionDTO request) {
        return stateAction(id, key, request, "READY", "ARCHIVED", "ARCHIVE_COLLECTION_PLAN");
    }

    @Transactional
    public Map<String, Object> cancel(Long id, String key, CollectionVersionDTO request) {
        return support.idempotent("CANCEL_COLLECTION_PLAN:" + id, key, request, () -> {
            CollectionPlanRow plan = requireVisible(id);
            access.requireManage(plan);
            requireVersion(plan, request.getVersion());
            if (!("DRAFT".equals(plan.getStatus()) || "READY".equals(plan.getStatus()))) {
                throw CollectionApiException.invalid();
            }
            if (mapper.movePlan(id, plan.getStatus(), "CANCELED", request.getVersion(), LocalDateTime.now()) != 1) {
                throw versionConflict(id);
            }
            support.audit(key.trim(), "CANCEL", "COLLECTION_PLAN", id, plan.getProjectId(),
                    plan.getStatus(), "CANCELED", trim(request.getCompletionReason()));
            return action(plan, "CANCELED", request.getVersion() + 1);
        });
    }

    private Map<String, Object> createPlan(Long projectId, CollectionPlanCreateDTO request, String requestId) {
        Long actorId = support.requiredUserId();
        CollectionPlanRow project = mapper.selectProjectContext(projectId, actorId);
        if (project == null) {
            throw CollectionApiException.projectNotFound();
        }
        access.requireProjectManage(project);
        requireName(request.getName());
        validateMetrics(request.getMetricDefinitions());
        validateOwnerAndLink(projectId, request.getOwnerId(), request.getLinkedExperimentRecordId());
        LocalDateTime now = LocalDateTime.now();
        CollectionPlanRow plan = new CollectionPlanRow();
        plan.setPlanCode("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        plan.setProjectId(projectId);
        plan.setName(request.getName().trim());
        plan.setPurpose(request.getPurpose().trim());
        plan.setOwnerId(request.getOwnerId());
        plan.setSourceType(request.getSourceType());
        plan.setMetricDefinitionsJson(support.writeJson(request.getMetricDefinitions()));
        plan.setLinkedExperimentRecordId(request.getLinkedExperimentRecordId());
        plan.setRetentionNote(trim(request.getRetentionNote()));
        plan.setCreatedBy(actorId);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        mapper.insertPlan(plan);
        String code = "CP-" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "-" + String.format("%04d", plan.getId());
        mapper.assignPlanCode(plan.getId(), code);
        support.audit(requestId, "CREATE", "COLLECTION_PLAN", plan.getId(), projectId, null, "DRAFT", null);
        plan.setPlanCode(code);
        plan.setStatus("DRAFT");
        plan.setVersion(1);
        return action(plan, "DRAFT", 1);
    }

    private Map<String, Object> stateAction(Long id, String key, CollectionVersionDTO request,
                                            String from, String to, String operation) {
        return support.idempotent(operation + ":" + id, key, request, () -> {
            CollectionPlanRow plan = requireVisible(id);
            access.requireManage(plan);
            requireVersion(plan, request.getVersion());
            if (!from.equals(plan.getStatus())) {
                throw CollectionApiException.invalid();
            }
            if ("READY".equals(to)) {
                validateOwnerAndLink(plan.getProjectId(), plan.getOwnerId(), plan.getLinkedExperimentRecordId());
            }
            if (mapper.movePlan(id, from, to, request.getVersion(), LocalDateTime.now()) != 1) {
                throw versionConflict(id);
            }
            support.audit(key.trim(), operation, "COLLECTION_PLAN", id, plan.getProjectId(), from, to,
                    trim(request.getCompletionReason()));
            return action(plan, to, request.getVersion() + 1);
        });
    }

    private CollectionPlanRow requireVisible(Long id) {
        CollectionPlanRow plan = id == null ? null : mapper.selectPlan(id, support.requiredUserId());
        access.requirePlanRead(plan);
        return plan;
    }

    private void validateOwnerAndLink(Long projectId, Long ownerId, Long recordId) {
        if (!OWNER_ROLES.contains(mapper.selectProjectRole(projectId, ownerId))) {
            throw CollectionApiException.invalid();
        }
        if (recordId != null && !projectId.equals(mapper.selectExperimentProject(recordId))) {
            throw CollectionApiException.invalid();
        }
    }

    private void validateMetrics(List<MetricDefinitionDTO> metrics) {
        long distinct = metrics.stream().map(metric -> metric.getCode().trim()).distinct().count();
        if (distinct != metrics.size()) {
            throw CollectionApiException.invalid();
        }
    }

    private void requireName(String name) {
        if (!StringUtils.hasText(name) || name.trim().length() < 2) {
            throw CollectionApiException.invalid();
        }
    }

    private void requireVersion(CollectionPlanRow plan, Integer version) {
        if (!plan.getVersion().equals(version)) {
            throw CollectionApiException.versionConflict(plan.getVersion());
        }
    }

    private CollectionApiException versionConflict(Long id) {
        CollectionPlanRow current = mapper.selectPlan(id, support.requiredUserId());
        return CollectionApiException.versionConflict(current == null ? 1 : current.getVersion());
    }

    private Map<String, Object> view(CollectionPlanRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.getId());
        result.put("code", row.getPlanCode());
        result.put("projectId", row.getProjectId());
        result.put("projectName", row.getProjectName());
        result.put("name", row.getName());
        result.put("purpose", row.getPurpose());
        result.put("ownerId", row.getOwnerId());
        result.put("ownerName", row.getOwnerName());
        result.put("sourceType", row.getSourceType());
        result.put("metricDefinitions", support.readJson(row.getMetricDefinitionsJson(), new TypeReference<List<Map<String, Object>>>() { }));
        result.put("linkedExperimentRecordId", row.getLinkedExperimentRecordId());
        result.put("linkedExperimentRecordCode", row.getLinkedExperimentRecordCode());
        result.put("retentionNote", row.getRetentionNote());
        result.put("status", row.getStatus());
        result.put("version", row.getVersion());
        result.put("createdBy", row.getCreatedBy());
        result.put("createTime", row.getCreateTime());
        result.put("updateTime", row.getUpdateTime());
        result.put("batchCount", row.getBatchCount() == null ? 0 : row.getBatchCount());
        if (row.getLatestBatchId() == null) {
            result.put("latestBatch", null);
        } else {
            Map<String, Object> latest = new LinkedHashMap<>();
            latest.put("id", row.getLatestBatchId());
            latest.put("batchCode", row.getLatestBatchCode());
            latest.put("status", row.getLatestBatchStatus());
            latest.put("createTime", row.getLatestBatchCreateTime());
            result.put("latestBatch", latest);
        }
        return result;
    }

    private Map<String, Object> action(CollectionPlanRow plan, String status, int version) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", plan.getId());
        result.put("code", plan.getPlanCode());
        result.put("status", status);
        result.put("version", version);
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

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
