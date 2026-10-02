package com.usn.labhub.user.service;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.project.TaskCreateDTO;
import com.usn.labhub.user.domain.dto.project.TaskStatusUpdateDTO;
import com.usn.labhub.user.domain.dto.project.TaskUpdateDTO;
import com.usn.labhub.user.domain.entity.project.ProjectAccessRecord;
import com.usn.labhub.user.domain.entity.project.TaskRecord;
import com.usn.labhub.user.domain.vo.project.TaskPageVO;
import com.usn.labhub.user.domain.vo.project.TaskStatusVO;
import com.usn.labhub.user.domain.vo.project.TaskVO;
import com.usn.labhub.user.mapper.ProjectMapper;
import com.usn.labhub.user.mapper.ProjectMilestoneMapper;
import com.usn.labhub.user.mapper.ProjectTaskMapper;
import com.usn.labhub.user.project.ProjectApiException;
import com.usn.labhub.user.project.TaskStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectTaskService {

    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELED");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createTime", "t.create_time",
            "dueDate", "t.due_date",
            "priority", "CASE t.priority WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END",
            "updateTime", "t.update_time");
    private final ProjectMapper projectMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectTaskMapper taskMapper;

    public ProjectTaskService(ProjectMapper projectMapper, ProjectMilestoneMapper milestoneMapper,
                              ProjectTaskMapper taskMapper) {
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
        this.taskMapper = taskMapper;
    }

    @Transactional
    public TaskVO create(Long projectId, TaskCreateDTO request) {
        ProjectAccessRecord access = requireManagerAndMutableProject(projectId);
        validateReferences(projectId, request.getMilestoneId(), request.getAssigneeUserId());

        TaskRecord task = new TaskRecord();
        task.setProjectId(access.getId());
        task.setMilestoneId(request.getMilestoneId());
        task.setTitle(normalizeTitle(request.getTitle()));
        task.setDescription(trimToNull(request.getDescription()));
        task.setStatus("TODO");
        task.setAssigneeUserId(request.getAssigneeUserId());
        task.setPriority(request.getPriority() == null ? "MEDIUM" : request.getPriority());
        task.setDueDate(request.getDueDate());
        task.setVersion(1);
        task.setCreatedBy(requiredUserId());
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskMapper.insert(task);
        return taskMapper.selectById(projectId, task.getId());
    }

    public TaskPageVO list(Long projectId, Long milestoneId, String status, Long assigneeUserId,
                           int page, int pageSize, String sortBy, String sortOrder) {
        requireReadableProject(projectId);
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw ProjectApiException.invalidParameter();
        }
        if (milestoneId != null && milestoneId < 0 || assigneeUserId != null && assigneeUserId < 1
                || page < 1 || pageSize < 1 || pageSize > 100) {
            throw ProjectApiException.invalidParameter();
        }
        String sortColumn = SORT_COLUMNS.get(sortBy);
        String normalizedOrder = sortOrder == null ? null : sortOrder.toUpperCase();
        if (sortColumn == null || !("ASC".equals(normalizedOrder) || "DESC".equals(normalizedOrder))) {
            throw ProjectApiException.invalidParameter();
        }
        long offsetLong = (long) (page - 1) * pageSize;
        if (offsetLong > Integer.MAX_VALUE) {
            throw ProjectApiException.invalidParameter();
        }
        boolean includeCanceled = "CANCELED".equals(normalizedStatus);
        long total = taskMapper.count(projectId, milestoneId, normalizedStatus, assigneeUserId, includeCanceled);
        List<TaskVO> tasks = total == 0 ? List.of() : taskMapper.selectPage(
                projectId, milestoneId, normalizedStatus, assigneeUserId, includeCanceled,
                sortColumn, normalizedOrder, (int) offsetLong, pageSize);
        return new TaskPageVO(total, page, pageSize, tasks);
    }

    public TaskVO detail(Long projectId, Long taskId) {
        requireReadableProject(projectId);
        return requireTask(projectId, taskId);
    }

    @Transactional
    public TaskVO update(Long projectId, Long taskId, TaskUpdateDTO request) {
        requireManagerAndMutableProject(projectId);
        TaskVO current = requireTask(projectId, taskId);
        requireCurrentVersion(current, request.getVersion());
        validateReferences(projectId, request.getMilestoneId(), request.getAssigneeUserId());

        TaskRecord task = new TaskRecord();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setMilestoneId(request.getMilestoneId());
        task.setTitle(normalizeTitle(request.getTitle()));
        task.setDescription(trimToNull(request.getDescription()));
        task.setAssigneeUserId(request.getAssigneeUserId());
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());
        task.setUpdateTime(LocalDateTime.now());
        if (taskMapper.updateDetails(task, request.getVersion()) != 1) {
            throw versionConflictForCurrentTask(projectId, taskId);
        }
        return requireTask(projectId, taskId);
    }

    @Transactional
    public TaskStatusVO updateStatus(Long projectId, Long taskId, TaskStatusUpdateDTO request) {
        ProjectAccessRecord access = requireReadableProject(projectId);
        requireMutableProject(access);
        TaskVO current = requireTask(projectId, taskId);
        requireStatusUpdatePermission(access, current, request.getStatus());
        requireCurrentVersion(current, request.getVersion());
        if (!TaskStateMachine.canTransition(current.getStatus(), request.getStatus())) {
            throw ProjectApiException.taskInvalidTransition();
        }

        String blockReason = current.getBlockReason();
        if ("BLOCKED".equals(request.getStatus())) {
            blockReason = trimToNull(request.getBlockReason());
            if (blockReason == null || blockReason.length() < 2) {
                throw ProjectApiException.blockReasonRequired();
            }
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.updateStatus(projectId, taskId, current.getStatus(), request.getStatus(),
                blockReason, request.getVersion(), now);
        if (updated != 1) {
            throw versionConflictForCurrentTask(projectId, taskId);
        }
        return new TaskStatusVO(taskId, request.getStatus(), request.getVersion() + 1, now);
    }

    private void validateReferences(Long projectId, Long milestoneId, Long assigneeUserId) {
        if (milestoneId != null && milestoneMapper.selectById(projectId, milestoneId) == null) {
            throw ProjectApiException.invalidMilestoneReference();
        }
        if (assigneeUserId != null && projectMapper.selectMember(projectId, assigneeUserId) == null) {
            throw ProjectApiException.assigneeNotMember();
        }
    }

    private ProjectAccessRecord requireReadableProject(Long projectId) {
        if (projectId == null || projectId < 1) {
            throw ProjectApiException.notFound();
        }
        ProjectAccessRecord access = projectMapper.selectProjectAccess(projectId, requiredUserId());
        Set<String> roles = UserContext.getRoles();
        boolean globalRead = roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER");
        if (access == null || (!globalRead && access.getMyRole() == null)) {
            throw ProjectApiException.notFound();
        }
        return access;
    }

    private ProjectAccessRecord requireManagerAndMutableProject(Long projectId) {
        ProjectAccessRecord access = requireReadableProject(projectId);
        boolean systemAdmin = UserContext.getRoles().contains("SYSTEM_ADMIN");
        if (!systemAdmin && !("OWNER".equals(access.getMyRole()) || "MAINTAINER".equals(access.getMyRole()))) {
            throw ProjectApiException.operationDenied();
        }
        requireMutableProject(access);
        return access;
    }

    private void requireMutableProject(ProjectAccessRecord access) {
        switch (access.getStatus()) {
            case "PAUSED" -> throw ProjectApiException.paused();
            case "COMPLETED" -> throw ProjectApiException.completed();
            case "ARCHIVED" -> throw ProjectApiException.archivedProject();
            default -> {
            }
        }
    }

    private void requireStatusUpdatePermission(ProjectAccessRecord access, TaskVO task, String targetStatus) {
        boolean manager = UserContext.getRoles().contains("SYSTEM_ADMIN")
                || "OWNER".equals(access.getMyRole()) || "MAINTAINER".equals(access.getMyRole());
        if (manager) {
            return;
        }
        boolean ownTask = "MEMBER".equals(access.getMyRole())
                && requiredUserId().equals(task.getAssigneeUserId());
        if (!ownTask || "CANCELED".equals(targetStatus)) {
            throw ProjectApiException.operationDenied();
        }
    }

    private TaskVO requireTask(Long projectId, Long taskId) {
        if (taskId == null || taskId < 1) {
            throw ProjectApiException.taskNotFound();
        }
        TaskVO task = taskMapper.selectById(projectId, taskId);
        if (task == null) {
            throw ProjectApiException.taskNotFound();
        }
        return task;
    }

    private void requireCurrentVersion(TaskVO task, Integer expectedVersion) {
        if (!task.getVersion().equals(expectedVersion)) {
            throw ProjectApiException.versionConflict(task.getId(), task.getVersion());
        }
    }

    private ProjectApiException versionConflictForCurrentTask(Long projectId, Long taskId) {
        Integer currentVersion = taskMapper.selectVersionForUpdate(projectId, taskId);
        if (currentVersion == null) {
            return ProjectApiException.taskNotFound();
        }
        return ProjectApiException.versionConflict(taskId, currentVersion);
    }

    private String normalizeTitle(String title) {
        String normalized = trimToNull(title);
        if (normalized == null || normalized.length() < 2 || normalized.length() > 120) {
            throw ProjectApiException.invalidParameter();
        }
        return normalized;
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw ProjectApiException.invalidParameter();
        }
        return userId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
