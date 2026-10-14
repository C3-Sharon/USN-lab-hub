package com.usn.labhub.user.service;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateCreateDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateCopyDTO;
import com.usn.labhub.user.domain.dto.learning.ExperimentTemplateUpdateDTO;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateAccessRecord;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateRecord;
import com.usn.labhub.user.domain.entity.learning.ExperimentTemplateVersionRecord;
import com.usn.labhub.user.domain.entity.project.ProjectAccessRecord;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplatePageVO;
import com.usn.labhub.user.domain.vo.learning.ExperimentTemplateVO;
import com.usn.labhub.user.learning.template.ExperimentTemplateApiException;
import com.usn.labhub.user.mapper.ExperimentTemplateMapper;
import com.usn.labhub.user.mapper.ProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExperimentTemplateService {
    private static final Set<String> SCOPES = Set.of("SYSTEM", "PROJECT");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "DEPRECATED", "ALL");
    private static final Set<String> PROJECT_MANAGERS = Set.of("OWNER", "MAINTAINER");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createTime", "t.create_time",
            "updateTime", "t.update_time",
            "title", "t.title");

    private final ExperimentTemplateMapper templateMapper;
    private final ProjectMapper projectMapper;

    public ExperimentTemplateService(ExperimentTemplateMapper templateMapper, ProjectMapper projectMapper) {
        this.templateMapper = templateMapper;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public ExperimentTemplateVO create(ExperimentTemplateCreateDTO request) {
        Long userId = requiredUserId();
        ensureNotGuest();
        String scope = normalizeUpper(request.getScope());
        if (!SCOPES.contains(scope)) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        String title = request.getTitle().trim();
        if (title.length() < 2) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        Long projectId = request.getProjectId();
        boolean globalManager = isGlobalManager();
        if ("SYSTEM".equals(scope)) {
            if (projectId != null) {
                throw ExperimentTemplateApiException.invalidRequest();
            }
            if (!globalManager) {
                throw ExperimentTemplateApiException.operationDenied();
            }
        } else {
            if (projectId == null || projectId < 1) {
                throw ExperimentTemplateApiException.invalidRequest();
            }
            ProjectAccessRecord project = projectMapper.selectProjectAccess(projectId, userId);
            if (project == null) {
                throw ExperimentTemplateApiException.projectNotFound();
            }
            if (!globalManager && !PROJECT_MANAGERS.contains(project.getMyRole())) {
                throw ExperimentTemplateApiException.operationDenied();
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ExperimentTemplateRecord template = new ExperimentTemplateRecord();
        template.setTitle(title);
        template.setDescription(trimToNull(request.getDescription()));
        template.setScope(scope);
        template.setProjectId(projectId);
        template.setCategory(trimToNull(request.getCategory()));
        template.setCreatedBy(userId);
        template.setCreateTime(now);
        template.setUpdateTime(now);
        templateMapper.insertTemplate(template);
        templateMapper.insertInitialDraft(template.getId(), userId, now);
        return withoutDraftFlag(requiredVisibleDetail(template.getId(), userId, true));
    }

    public ExperimentTemplatePageVO list(Integer page, Integer pageSize, String scope, Long projectId,
                                         String category, String status, String keyword,
                                         String sortBy, String sortOrder) {
        Long userId = requiredUserId();
        ensureNotGuest();
        if (page == null || page < 1 || pageSize == null || pageSize < 1 || pageSize > 100) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        String normalizedScope = normalizeUpper(scope);
        if (normalizedScope != null && !SCOPES.contains(normalizedScope)) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        if (projectId != null && (!"PROJECT".equals(normalizedScope) || projectId < 1)) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        String normalizedStatus = normalizeUpper(status);
        if (!STATUSES.contains(normalizedStatus)) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        String normalizedSortBy = StringUtils.hasText(sortBy) ? sortBy.trim() : "updateTime";
        String sortColumn = SORT_COLUMNS.get(normalizedSortBy);
        String normalizedOrder = StringUtils.hasText(sortOrder) ? sortOrder.trim().toLowerCase() : "desc";
        if (sortColumn == null || !("asc".equals(normalizedOrder) || "desc".equals(normalizedOrder))) {
            throw ExperimentTemplateApiException.invalidRequest();
        }

        boolean globalManager = isGlobalManager();
        String normalizedCategory = trimToNull(category);
        String normalizedKeyword = trimToNull(keyword);
        long total = templateMapper.countVisible(userId, globalManager, normalizedScope, projectId,
                normalizedCategory, normalizedStatus, normalizedKeyword);
        List<ExperimentTemplateVO> templates = total == 0 ? List.of() : templateMapper.selectVisible(
                userId, globalManager, normalizedScope, projectId, normalizedCategory, normalizedStatus,
                normalizedKeyword, sortColumn, normalizedOrder, (page - 1) * pageSize, pageSize);
        templates.forEach(this::withoutDraftFlag);
        return new ExperimentTemplatePageVO(total, page, pageSize, templates);
    }

    public ExperimentTemplateVO detail(Long templateId) {
        Long userId = requiredUserId();
        ensureNotGuest();
        if (templateId == null || templateId < 1) {
            throw ExperimentTemplateApiException.notFound();
        }
        return requiredVisibleDetail(templateId, userId, isGlobalManager());
    }

    @Transactional
    public ExperimentTemplateVO update(Long templateId, ExperimentTemplateUpdateDTO request) {
        Long userId = requiredUserId();
        ensureNotGuest();
        if (templateId == null || templateId < 1) {
            throw ExperimentTemplateApiException.notFound();
        }
        boolean globalManager = isGlobalManager();
        ExperimentTemplateAccessRecord access = templateMapper.selectAccess(templateId, userId);
        if (access == null || !canView(access, globalManager)) {
            throw ExperimentTemplateApiException.notFound();
        }
        if (!canManage(access, globalManager)) {
            throw ExperimentTemplateApiException.operationDenied();
        }
        String title = request.getTitle().trim();
        if (title.length() < 2) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        templateMapper.updateMetadata(templateId, title, trimToNull(request.getDescription()),
                trimToNull(request.getCategory()), LocalDateTime.now());
        return withoutDraftFlag(requiredVisibleDetail(templateId, userId, globalManager));
    }

    @Transactional
    public ExperimentTemplateVO copy(Long templateId, ExperimentTemplateCopyDTO request) {
        Long userId = requiredUserId();
        ensureNotGuest();
        if (templateId == null || templateId < 1) {
            throw ExperimentTemplateApiException.notFound();
        }

        boolean globalManager = isGlobalManager();
        ExperimentTemplateVO source = requiredVisibleDetail(templateId, userId, globalManager);
        if (!"SYSTEM".equals(source.getScope())) {
            throw ExperimentTemplateApiException.notSystemTemplate();
        }

        ProjectAccessRecord targetProject = projectMapper.selectProjectAccess(request.getTargetProjectId(), userId);
        if (targetProject == null) {
            throw ExperimentTemplateApiException.projectNotFound();
        }
        if (!globalManager && !PROJECT_MANAGERS.contains(targetProject.getMyRole())) {
            throw ExperimentTemplateApiException.operationDenied();
        }

        String title = StringUtils.hasText(request.getTitle())
                ? request.getTitle().trim() : defaultCopyTitle(source.getTitle(), targetProject.getProjectName());
        if (title.length() < 2 || title.length() > 120) {
            throw ExperimentTemplateApiException.invalidRequest();
        }
        String description = request.getDescription() == null
                ? source.getDescription() : trimToNull(request.getDescription());
        LocalDateTime now = LocalDateTime.now();

        ExperimentTemplateRecord copied = new ExperimentTemplateRecord();
        copied.setTitle(title);
        copied.setDescription(description);
        copied.setScope("PROJECT");
        copied.setProjectId(request.getTargetProjectId());
        copied.setCategory(source.getCategory());
        copied.setCreatedBy(userId);
        copied.setCreateTime(now);
        copied.setUpdateTime(now);
        templateMapper.insertTemplate(copied);

        ExperimentTemplateVersionRecord sourceVersion = templateMapper.selectLatestPublished(templateId);
        ExperimentTemplateVersionRecord draft = new ExperimentTemplateVersionRecord();
        draft.setTemplateId(copied.getId());
        draft.setVersion("0.1.0");
        draft.setStatus("DRAFT");
        draft.setSchemaJson(sourceVersion == null ? "{}" : sourceVersion.getSchemaJson());
        draft.setFieldCount(sourceVersion == null ? 0 : sourceVersion.getFieldCount());
        draft.setCreatedBy(userId);
        draft.setCreateTime(now);
        draft.setUpdateTime(now);
        templateMapper.insertDraftVersion(draft);

        return withoutDraftFlag(requiredVisibleDetail(copied.getId(), userId, true));
    }

    private ExperimentTemplateVO requiredVisibleDetail(Long templateId, Long userId, boolean globalManager) {
        ExperimentTemplateVO template = templateMapper.selectVisibleDetail(templateId, userId, globalManager);
        if (template == null) {
            throw ExperimentTemplateApiException.notFound();
        }
        return template;
    }

    private ExperimentTemplateVO withoutDraftFlag(ExperimentTemplateVO template) {
        template.setHasDraft(null);
        return template;
    }

    private boolean canView(ExperimentTemplateAccessRecord access, boolean globalManager) {
        if (globalManager) {
            return true;
        }
        if ("SYSTEM".equals(access.getScope())) {
            return access.isHasPublished();
        }
        return access.getProjectRole() != null
                && (PROJECT_MANAGERS.contains(access.getProjectRole()) || access.isHasPublished());
    }

    private boolean canManage(ExperimentTemplateAccessRecord access, boolean globalManager) {
        if (globalManager) {
            return true;
        }
        return "PROJECT".equals(access.getScope()) && PROJECT_MANAGERS.contains(access.getProjectRole());
    }

    private boolean isGlobalManager() {
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER");
    }

    private void ensureNotGuest() {
        if (UserContext.getRoles().contains("GUEST")) {
            throw ExperimentTemplateApiException.accessDenied();
        }
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw ExperimentTemplateApiException.accessDenied();
        }
        return userId;
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultCopyTitle(String sourceTitle, String projectName) {
        String suffix = "（" + projectName + "）";
        int sourceLength = Math.max(1, 120 - suffix.length());
        String normalizedSource = sourceTitle.length() > sourceLength
                ? sourceTitle.substring(0, sourceLength) : sourceTitle;
        String title = normalizedSource + suffix;
        return title.length() > 120 ? title.substring(0, 120) : title;
    }
}
