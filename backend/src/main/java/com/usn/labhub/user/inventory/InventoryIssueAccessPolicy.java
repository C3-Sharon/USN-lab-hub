package com.usn.labhub.user.inventory;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.domain.entity.project.ProjectAccessRecord;
import com.usn.labhub.user.mapper.ProjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class InventoryIssueAccessPolicy {
    private static final Set<String> ACCESS_ROLES = Set.of(
            "SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER");
    private static final Set<String> PROJECT_MANAGER_ROLES = Set.of("OWNER", "MAINTAINER");

    private final ProjectMapper projectMapper;

    public InventoryIssueAccessPolicy(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public void requireCreate() {
        if (UserContext.getRoles().stream().noneMatch(ACCESS_ROLES::contains)) {
            throw InventoryIssueApiException.operationDenied();
        }
    }

    public ProjectAccessRecord requireVisibleProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        ProjectAccessRecord project = projectMapper.selectProjectAccess(projectId, userId());
        if (project == null || (!isSystemAdmin() && project.getMyRole() == null)) {
            throw InventoryIssueApiException.projectNotFound();
        }
        return project;
    }

    public boolean canViewAll() {
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER")
                || roles.contains("STOCK_KEEPER");
    }

    public boolean canViewCost() {
        return canViewAll();
    }

    public boolean canView(InventoryIssueRequestRecord request) {
        if (canViewAll() || userId().equals(request.getRequesterId())) {
            return true;
        }
        if (request.getProjectId() == null) {
            return false;
        }
        ProjectAccessRecord project = projectMapper.selectProjectAccess(request.getProjectId(), userId());
        return project != null && PROJECT_MANAGER_ROLES.contains(project.getMyRole());
    }

    public void requireEdit(InventoryIssueRequestRecord request) {
        if (isSystemAdmin() || userId().equals(request.getRequesterId())) {
            return;
        }
        throw InventoryIssueApiException.operationDenied();
    }

    public void requireSubmitOrCancel(InventoryIssueRequestRecord request) {
        if (isSystemAdmin() || userId().equals(request.getRequesterId())) {
            return;
        }
        throw InventoryIssueApiException.operationDenied();
    }

    public void requireApprove(InventoryIssueRequestRecord request) {
        if (userId().equals(request.getRequesterId())) {
            throw InventoryIssueApiException.selfApproval();
        }
        if (isSystemAdmin() || UserContext.getRoles().contains("TEACHER")) {
            return;
        }
        throw InventoryIssueApiException.approvalDenied();
    }

    public void requirePolicyManagement() {
        if (!isSystemAdmin()) {
            throw InventoryIssueApiException.policyDenied();
        }
    }

    public Long userId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw InventoryIssueApiException.operationDenied();
        }
        return userId;
    }

    public boolean isSystemAdmin() {
        return UserContext.getRoles().contains("SYSTEM_ADMIN");
    }
}
