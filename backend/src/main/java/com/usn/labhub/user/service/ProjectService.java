package com.usn.labhub.user.service;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.project.ProjectCreateDTO;
import com.usn.labhub.user.domain.entity.project.ProjectRecord;
import com.usn.labhub.user.domain.vo.project.ProjectDetailVO;
import com.usn.labhub.user.domain.vo.project.ProjectPageVO;
import com.usn.labhub.user.domain.vo.project.ProjectSummaryVO;
import com.usn.labhub.user.mapper.ProjectMapper;
import com.usn.labhub.user.project.ProjectApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectService {

    private static final Set<String> STATUSES = Set.of(
            "PREPARING", "ACTIVE", "PAUSED", "COMPLETED", "ARCHIVED");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createTime", "p.create_time",
            "updateTime", "p.update_time",
            "name", "p.project_name",
            "code", "p.project_code");

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Transactional
    public ProjectSummaryVO create(ProjectCreateDTO request) {
        Long userId = requiredUserId();
        LocalDateTime now = LocalDateTime.now();
        String projectName = request.getName().trim();
        if (projectName.length() < 2) {
            throw ProjectApiException.badRequest();
        }

        ProjectRecord project = new ProjectRecord();
        project.setProjectCode(request.getCode().trim());
        project.setProjectName(projectName);
        project.setDescription(trimToNull(request.getSummary()));
        project.setOwnerId(userId);
        project.setStatus("PREPARING");
        project.setPublicVisible(false);
        project.setCategory(trimToNull(request.getCategory()));
        project.setCoverMediaId(request.getCoverMediaId());
        project.setCreateTime(now);
        project.setUpdateTime(now);

        try {
            projectMapper.insertProject(project);
        } catch (DuplicateKeyException exception) {
            throw ProjectApiException.duplicateCode();
        }
        projectMapper.insertMember(project.getId(), userId, "OWNER", now);

        ProjectDetailVO created = projectMapper.selectProjectDetail(project.getId(), userId, true, false);
        return created;
    }

    public ProjectPageVO list(Integer page, Integer pageSize, String status, String keyword,
                              String sortBy, String sortOrder) {
        if (page == null || page < 1 || pageSize == null || pageSize < 1 || pageSize > 50) {
            throw ProjectApiException.badRequest();
        }
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw ProjectApiException.badRequest();
        }
        String normalizedSortBy = StringUtils.hasText(sortBy) ? sortBy.trim() : "updateTime";
        String sortColumn = SORT_COLUMNS.get(normalizedSortBy);
        String normalizedOrder = StringUtils.hasText(sortOrder) ? sortOrder.trim().toLowerCase() : "desc";
        if (sortColumn == null || !("asc".equals(normalizedOrder) || "desc".equals(normalizedOrder))) {
            throw ProjectApiException.badRequest();
        }

        Long userId = requiredUserId();
        AccessScope scope = accessScope();
        String normalizedKeyword = trimToNull(keyword);
        long total = projectMapper.countProjects(userId, scope.viewAll(), scope.publicOnly(),
                normalizedStatus, normalizedKeyword);
        List<ProjectSummaryVO> list = total == 0 ? List.of() : projectMapper.selectProjects(
                userId, scope.viewAll(), scope.publicOnly(), normalizedStatus, normalizedKeyword,
                sortColumn, normalizedOrder, (page - 1) * pageSize, pageSize);
        return new ProjectPageVO(total, page, pageSize, list);
    }

    public ProjectDetailVO detail(Long projectId) {
        if (projectId == null || projectId < 1) {
            throw ProjectApiException.notFound();
        }
        Long userId = requiredUserId();
        AccessScope scope = accessScope();
        ProjectDetailVO project = projectMapper.selectProjectDetail(
                projectId, userId, scope.viewAll(), scope.publicOnly());
        if (project == null) {
            throw ProjectApiException.notFound();
        }
        project.setMembers(projectMapper.selectMembers(projectId));
        return project;
    }

    private AccessScope accessScope() {
        Set<String> roles = UserContext.getRoles();
        boolean viewAll = roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER");
        boolean publicOnly = !viewAll && roles.contains("GUEST");
        return new AccessScope(viewAll, publicOnly);
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw ProjectApiException.badRequest();
        }
        return userId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record AccessScope(boolean viewAll, boolean publicOnly) {
    }
}
