package com.usn.labhub.user.service;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.project.MilestoneCreateDTO;
import com.usn.labhub.user.domain.dto.project.MilestoneStatusUpdateDTO;
import com.usn.labhub.user.domain.entity.project.MilestoneRecord;
import com.usn.labhub.user.domain.entity.project.ProjectAccessRecord;
import com.usn.labhub.user.domain.vo.project.MilestoneStatusVO;
import com.usn.labhub.user.domain.vo.project.MilestoneVO;
import com.usn.labhub.user.mapper.ProjectMapper;
import com.usn.labhub.user.mapper.ProjectMilestoneMapper;
import com.usn.labhub.user.project.ProjectApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ProjectMilestoneService {

    private static final Set<String> STATUSES = Set.of("PLANNED", "IN_PROGRESS", "COMPLETED");

    private final ProjectMapper projectMapper;
    private final ProjectMilestoneMapper milestoneMapper;

    public ProjectMilestoneService(ProjectMapper projectMapper, ProjectMilestoneMapper milestoneMapper) {
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
    }

    @Transactional
    public MilestoneVO create(Long projectId, MilestoneCreateDTO request) {
        ProjectAccessRecord access = requireWritableProject(projectId);
        String name = request.getName().trim();
        if (name.length() < 2) {
            throw ProjectApiException.invalidParameter();
        }
        MilestoneRecord milestone = new MilestoneRecord();
        milestone.setProjectId(access.getId());
        milestone.setName(name);
        milestone.setDescription(trimToNull(request.getDescription()));
        milestone.setStatus(request.getStatus());
        milestone.setStartDate(request.getStartDate());
        milestone.setEndDate(request.getEndDate());
        milestone.setSortOrder(request.getSortOrder());
        LocalDateTime now = LocalDateTime.now();
        milestone.setCreateTime(now);
        milestone.setUpdateTime(now);
        milestoneMapper.insert(milestone);
        return milestoneMapper.selectById(projectId, milestone.getId());
    }

    public List<MilestoneVO> list(Long projectId, String status) {
        requireReadableProject(projectId);
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw ProjectApiException.invalidParameter();
        }
        return milestoneMapper.selectByProject(projectId, normalizedStatus);
    }

    @Transactional
    public MilestoneStatusVO updateStatus(Long projectId, Long milestoneId,
                                          MilestoneStatusUpdateDTO request) {
        requireWritableProject(projectId);
        MilestoneVO current = milestoneMapper.selectById(projectId, milestoneId);
        if (current == null) {
            throw ProjectApiException.milestoneNotFound();
        }
        String targetStatus = request.getStatus();
        if (!isNextStatus(current.getStatus(), targetStatus)) {
            throw ProjectApiException.milestoneInvalidTransition();
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = milestoneMapper.updateStatus(
                projectId, milestoneId, current.getStatus(), targetStatus, now);
        if (updated != 1) {
            throw ProjectApiException.milestoneInvalidTransition();
        }
        return new MilestoneStatusVO(milestoneId, targetStatus, now);
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

    private ProjectAccessRecord requireWritableProject(Long projectId) {
        ProjectAccessRecord access = requireReadableProject(projectId);
        boolean systemAdmin = UserContext.getRoles().contains("SYSTEM_ADMIN");
        if (!systemAdmin && !("OWNER".equals(access.getMyRole()) || "MAINTAINER".equals(access.getMyRole()))) {
            throw ProjectApiException.operationDenied();
        }
        switch (access.getStatus()) {
            case "PAUSED" -> throw ProjectApiException.paused();
            case "COMPLETED" -> throw ProjectApiException.completed();
            case "ARCHIVED" -> throw ProjectApiException.archivedProject();
            default -> {
                return access;
            }
        }
    }

    private boolean isNextStatus(String currentStatus, String targetStatus) {
        return ("PLANNED".equals(currentStatus) && "IN_PROGRESS".equals(targetStatus))
                || ("IN_PROGRESS".equals(currentStatus) && "COMPLETED".equals(targetStatus));
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
