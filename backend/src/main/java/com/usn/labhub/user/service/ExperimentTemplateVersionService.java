package com.usn.labhub.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateVersionDeprecateDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateVersionPublishDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateVersionUpdateDTO;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateAccessRecord;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateVersionRecord;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateDeprecatedVersionVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePublishResultVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePublishedVersionVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVersionActionVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVersionPageVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVersionVO;
import com.usn.labhub.user.learning.template.ExperimentTemplateApiException;
import com.usn.labhub.user.learning.template.ExperimentTemplateSchemaValidator;
import com.usn.labhub.user.learning.template.TemplateValidationResult;
import com.usn.labhub.user.mapper.ExperimentTemplateMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ExperimentTemplateVersionService {
    private static final Set<String> VERSION_STATUSES = Set.of("DRAFT", "PUBLISHED", "DEPRECATED", "ALL");
    private static final Set<String> PROJECT_MANAGERS = Set.of("OWNER", "MAINTAINER");

    private final ExperimentTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;
    private final ExperimentTemplateSchemaValidator schemaValidator;

    public ExperimentTemplateVersionService(ExperimentTemplateMapper templateMapper, ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.objectMapper = objectMapper;
        this.schemaValidator = new ExperimentTemplateSchemaValidator(objectMapper);
    }

    public ExperimentTemplateVersionPageVO list(Long templateId, Integer page, Integer pageSize, String status) {
        if (page == null || page < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        String normalizedStatus = normalizeStatus(status);
        AccessDecision access = requireVisibleAccess(templateId);
        if ("DRAFT".equals(normalizedStatus) && !access.manage()) {
            throw ExperimentTemplateApiException.operationDenied();
        }
        long total = templateMapper.countVersions(templateId, normalizedStatus, access.manage());
        List<ExperimentTemplateVersionVO> versions = total == 0 ? List.of() : templateMapper.selectVersions(
                        templateId, normalizedStatus, access.manage(), (page - 1) * pageSize, pageSize)
                .stream().map(record -> toVersion(record, false)).toList();
        return new ExperimentTemplateVersionPageVO(total, page, pageSize, versions);
    }

    public ExperimentTemplateVersionVO detail(Long templateId, Long versionId) {
        AccessDecision access = requireVisibleAccess(templateId);
        ExperimentTemplateVersionRecord version = requiredVersion(templateId, versionId);
        if ("DRAFT".equals(version.getStatus()) && !access.manage()) {
            throw ExperimentTemplateApiException.versionNotFound();
        }
        return toVersion(version, true);
    }

    @Transactional
    public ExperimentTemplateVersionVO updateDraft(Long templateId, Long versionId,
                                                   ExperimentTemplateVersionUpdateDTO request) {
        requireManageAccess(templateId);
        ExperimentTemplateVersionRecord current = requiredVersion(templateId, versionId);
        if (!"DRAFT".equals(current.getStatus())) {
            throw ExperimentTemplateApiException.versionNotEditable();
        }
        TemplateValidationResult validation = schemaValidator.validateSchema(request.getSchemaJson());
        if (!validation.valid()) {
            throw ExperimentTemplateApiException.schemaInvalid(validation.errors());
        }
        String schemaJson = writeJson(request.getSchemaJson());
        int fieldCount = request.getSchemaJson().path("properties").size();
        int updated = templateMapper.updateDraft(templateId, versionId, schemaJson, fieldCount,
                trimToNull(request.getChangelog()), LocalDateTime.now());
        if (updated == 0) {
            throw ExperimentTemplateApiException.versionNotEditable();
        }
        return toVersion(requiredVersion(templateId, versionId), false);
    }

    public List<ExperimentTemplatePublishedVersionVO> published(Long templateId) {
        requireVisibleAccess(templateId);
        return templateMapper.selectPublishedVersions(templateId);
    }

    public TemplateValidationResult validateData(Long templateId, Long versionId, JsonNode data) {
        AccessDecision access = requireVisibleAccess(templateId);
        ExperimentTemplateVersionRecord version = requiredVersion(templateId, versionId);
        if ("DRAFT".equals(version.getStatus()) && !access.manage()) {
            throw ExperimentTemplateApiException.versionNotFound();
        }
        return schemaValidator.validateData(readJson(version.getSchemaJson()), data);
    }

    @Transactional
    public ExperimentTemplatePublishResultVO publish(Long templateId, Long versionId,
                                                     ExperimentTemplateVersionPublishDTO request) {
        requireManageAccess(templateId);
        Long userId = requiredUserId();
        ExperimentTemplateVersionRecord draft = requiredVersion(templateId, versionId);
        if (!"DRAFT".equals(draft.getStatus())) {
            throw ExperimentTemplateApiException.versionNotPublishable();
        }

        JsonNode schema = readJson(draft.getSchemaJson());
        if (schema.path("properties").size() == 0) {
            throw ExperimentTemplateApiException.schemaEmpty();
        }
        TemplateValidationResult validation = schemaValidator.validateSchema(schema);
        if (!validation.valid()) {
            throw ExperimentTemplateApiException.schemaInvalid(validation.errors());
        }

        DraftVersionNumbers numbers = nextVersionNumbers(draft.getVersion());
        LocalDateTime now = LocalDateTime.now();
        String changelog = request.getChangelog() == null
                ? draft.getChangelog() : trimToNull(request.getChangelog());
        try {
            int updated = templateMapper.publishDraft(templateId, versionId, numbers.publishedVersion(),
                    numbers.versionCode(), changelog, userId, now);
            if (updated == 0) {
                throw ExperimentTemplateApiException.versionConflict();
            }

            ExperimentTemplateVersionRecord newDraft = new ExperimentTemplateVersionRecord();
            newDraft.setTemplateId(templateId);
            newDraft.setVersion(numbers.nextDraftVersion());
            newDraft.setStatus("DRAFT");
            newDraft.setSchemaJson(draft.getSchemaJson());
            newDraft.setFieldCount(draft.getFieldCount());
            newDraft.setCreatedBy(userId);
            newDraft.setCreateTime(now);
            newDraft.setUpdateTime(now);
            templateMapper.insertDraftVersion(newDraft);
            templateMapper.updateTemplateAfterPublish(templateId, versionId,
                    numbers.publishedVersion(), now);

            ExperimentTemplateVersionRecord published = requiredVersion(templateId, versionId);
            return new ExperimentTemplatePublishResultVO(
                    toAction(published), toAction(requiredVersion(templateId, newDraft.getId())));
        } catch (DuplicateKeyException exception) {
            throw ExperimentTemplateApiException.versionConflict();
        }
    }

    @Transactional
    public ExperimentTemplateDeprecatedVersionVO deprecate(Long templateId, Long versionId,
                                                            ExperimentTemplateVersionDeprecateDTO request) {
        requireManageAccess(templateId);
        Long userId = requiredUserId();
        ExperimentTemplateVersionRecord current = requiredVersion(templateId, versionId);
        if (!"PUBLISHED".equals(current.getStatus())) {
            throw ExperimentTemplateApiException.versionNotDeprecatable();
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = templateMapper.deprecatePublished(templateId, versionId,
                request.getReason().trim(), userId, now);
        if (updated == 0) {
            throw ExperimentTemplateApiException.versionConflict();
        }
        ExperimentTemplateVersionRecord latest = templateMapper.selectLatestPublished(templateId);
        templateMapper.updateTemplateAfterDeprecate(templateId,
                latest == null ? null : latest.getId(), latest == null ? null : latest.getVersion(), now);
        return toDeprecated(requiredVersion(templateId, versionId));
    }

    private AccessDecision requireVisibleAccess(Long templateId) {
        Long userId = requiredUserId();
        if (templateId == null || templateId < 1) {
            throw ExperimentTemplateApiException.notFound();
        }
        boolean globalManager = isGlobalManager();
        ExperimentTemplateAccessRecord access = templateMapper.selectAccess(templateId, userId);
        if (access == null || !canViewVersions(access, globalManager)) {
            throw ExperimentTemplateApiException.notFound();
        }
        return new AccessDecision(access, canManage(access, globalManager));
    }

    private AccessDecision requireManageAccess(Long templateId) {
        AccessDecision access = requireVisibleAccess(templateId);
        if (!access.manage()) {
            throw ExperimentTemplateApiException.operationDenied();
        }
        return access;
    }

    private boolean canViewVersions(ExperimentTemplateAccessRecord access, boolean globalManager) {
        if (globalManager) {
            return true;
        }
        if ("SYSTEM".equals(access.getScope())) {
            return access.isHasFormalVersion();
        }
        return access.getProjectRole() != null
                && (PROJECT_MANAGERS.contains(access.getProjectRole()) || access.isHasFormalVersion());
    }

    private boolean canManage(ExperimentTemplateAccessRecord access, boolean globalManager) {
        return globalManager || ("PROJECT".equals(access.getScope())
                && PROJECT_MANAGERS.contains(access.getProjectRole()));
    }

    private ExperimentTemplateVersionRecord requiredVersion(Long templateId, Long versionId) {
        if (versionId == null || versionId < 1) {
            throw ExperimentTemplateApiException.versionNotFound();
        }
        ExperimentTemplateVersionRecord version = templateMapper.selectVersion(templateId, versionId);
        if (version == null) {
            throw ExperimentTemplateApiException.versionNotFound();
        }
        return version;
    }

    private ExperimentTemplateVersionVO toVersion(ExperimentTemplateVersionRecord record, boolean includeSchema) {
        ExperimentTemplateVersionVO version = new ExperimentTemplateVersionVO();
        version.setId(record.getId());
        version.setTemplateId(record.getTemplateId());
        version.setVersion(record.getVersion());
        version.setVersionCode(record.getVersionCode());
        version.setStatus(record.getStatus());
        version.setSchemaJson(includeSchema ? readJson(record.getSchemaJson()) : null);
        version.setFieldCount(record.getFieldCount());
        version.setChangelog(record.getChangelog());
        version.setPublishedBy(record.getPublishedBy());
        version.setPublishedByName(record.getPublishedByName());
        version.setPublishedAt(record.getPublishedAt());
        version.setDeprecatedReason(record.getDeprecatedReason());
        version.setDeprecatedBy(record.getDeprecatedBy());
        version.setDeprecatedAt(record.getDeprecatedAt());
        version.setCreatedBy(record.getCreatedBy());
        version.setCreateTime(record.getCreateTime());
        version.setUpdateTime(record.getUpdateTime());
        return version;
    }

    private ExperimentTemplateVersionActionVO toAction(ExperimentTemplateVersionRecord record) {
        ExperimentTemplateVersionActionVO version = new ExperimentTemplateVersionActionVO();
        version.setId(record.getId());
        version.setVersion(record.getVersion());
        version.setStatus(record.getStatus());
        version.setPublishedBy(record.getPublishedBy());
        version.setPublishedByName(record.getPublishedByName());
        version.setPublishedAt(record.getPublishedAt());
        return version;
    }

    private ExperimentTemplateDeprecatedVersionVO toDeprecated(ExperimentTemplateVersionRecord record) {
        ExperimentTemplateDeprecatedVersionVO version = new ExperimentTemplateDeprecatedVersionVO();
        version.setId(record.getId());
        version.setVersion(record.getVersion());
        version.setStatus(record.getStatus());
        version.setDeprecatedReason(record.getDeprecatedReason());
        version.setDeprecatedBy(record.getDeprecatedBy());
        version.setDeprecatedAt(record.getDeprecatedAt());
        version.setUpdateTime(record.getUpdateTime());
        return version;
    }

    private DraftVersionNumbers nextVersionNumbers(String draftVersion) {
        String[] parts = draftVersion == null ? new String[0] : draftVersion.split("\\.");
        if (parts.length != 3 || !"0".equals(parts[0]) || !"0".equals(parts[2])) {
            throw new IllegalStateException("数据库中的草稿版本号不符合 0.X.0 规则");
        }
        try {
            int draftMinor = Integer.parseInt(parts[1]);
            if (draftMinor < 1) {
                throw new NumberFormatException("draft minor must be positive");
            }
            int publishedMinor = draftMinor - 1;
            return new DraftVersionNumbers("1." + publishedMinor + ".0",
                    10000 + publishedMinor * 100, "0." + (draftMinor + 1) + ".0");
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("数据库中的草稿版本号不符合 0.X.0 规则", exception);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的模板 Schema 不是合法 JSON", exception);
        }
    }

    private String writeJson(JsonNode json) {
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException exception) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "ALL";
        if (!VERSION_STATUSES.contains(normalized)) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        return normalized;
    }

    private boolean isGlobalManager() {
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER");
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null || UserContext.getRoles().contains("GUEST")) {
            throw ExperimentTemplateApiException.accessDenied();
        }
        return userId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record AccessDecision(ExperimentTemplateAccessRecord access, boolean manage) {
    }

    private record DraftVersionNumbers(String publishedVersion, int versionCode, String nextDraftVersion) {
    }
}
