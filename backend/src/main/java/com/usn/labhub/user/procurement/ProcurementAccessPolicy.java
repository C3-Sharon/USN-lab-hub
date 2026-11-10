package com.usn.labhub.user.procurement;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.entity.project.ProjectAccessRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementOrderRecord;
import com.usn.labhub.user.mapper.ProjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ProcurementAccessPolicy {
    private static final Set<String> ACCESS_ROLES = Set.of(
            "SYSTEM_ADMIN", "TEACHER", "STOCK_KEEPER", "MEMBER");
    private final ProjectMapper projectMapper;

    public ProcurementAccessPolicy(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public void requireCreate() {
        if (UserContext.getRoles().stream().noneMatch(ACCESS_ROLES::contains)) {
            throw ProcurementApiException.operationDenied();
        }
    }

    public ProjectAccessRecord requireWritableProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        ProjectAccessRecord project = projectMapper.selectProjectAccess(projectId, userId());
        if (project == null) {
            throw ProcurementApiException.projectNotFound();
        }
        if (!isSystemAdmin() && !Set.of("OWNER", "MAINTAINER").contains(project.getMyRole())) {
            throw ProcurementApiException.projectNotFound();
        }
        return project;
    }

    public boolean canViewAll() {
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER") || roles.contains("STOCK_KEEPER");
    }

    public boolean canView(ProcurementOrderRecord order) {
        if (canViewAll() || userId().equals(order.getRequesterId()) || userId().equals(order.getBuyerId())) {
            return true;
        }
        if (order.getProjectId() == null) {
            return false;
        }
        ProjectAccessRecord project = projectMapper.selectProjectAccess(order.getProjectId(), userId());
        return project != null && Set.of("OWNER", "MAINTAINER").contains(project.getMyRole());
    }

    public void requireEdit(ProcurementOrderRecord order) {
        Set<String> roles = UserContext.getRoles();
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("STOCK_KEEPER")
                || userId().equals(order.getRequesterId())) {
            return;
        }
        throw ProcurementApiException.operationDenied();
    }

    public void requireSubmitOrCancel(ProcurementOrderRecord order) {
        if (isSystemAdmin() || userId().equals(order.getRequesterId())) {
            return;
        }
        throw ProcurementApiException.operationDenied();
    }

    public void requireApprove(ProcurementOrderRecord order) {
        Set<String> roles = UserContext.getRoles();
        if (userId().equals(order.getRequesterId())) {
            throw ProcurementApiException.selfApproval();
        }
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("TEACHER")) {
            return;
        }
        throw ProcurementApiException.approvalDenied();
    }

    public void requireOrderManagement() {
        Set<String> roles = UserContext.getRoles();
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("STOCK_KEEPER")) {
            return;
        }
        throw ProcurementApiException.operationDenied();
    }

    public void requireReceiptManagement() {
        Set<String> roles = UserContext.getRoles();
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("STOCK_KEEPER")) {
            return;
        }
        throw ProcurementApiException.receiptDenied();
    }

    public void requireOrderAttachmentWrite(ProcurementOrderRecord order) {
        Set<String> roles = UserContext.getRoles();
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("STOCK_KEEPER")) {
            return;
        }
        boolean own = userId().equals(order.getRequesterId()) || userId().equals(order.getBuyerId());
        if (roles.contains("TEACHER") && own) {
            return;
        }
        if (roles.contains("MEMBER") && own
                && ("DRAFT".equals(order.getStatus())
                || Set.of("ORDERED", "PARTIALLY_RECEIVED", "RECEIVED", "COMPLETED").contains(order.getStatus()))) {
            return;
        }
        throw ProcurementApiException.operationDenied();
    }

    public void requireReceiptAttachmentWrite() {
        requireReceiptManagement();
    }

    public void requireInventoryDetail() {
        if (!canViewAll()) {
            throw ProcurementApiException.operationDenied();
        }
    }

    public void requireCancel(ProcurementOrderRecord order) {
        Set<String> roles = UserContext.getRoles();
        boolean requester = userId().equals(order.getRequesterId());
        boolean admin = roles.contains("SYSTEM_ADMIN");
        boolean stockKeeper = roles.contains("STOCK_KEEPER");
        if (!Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "ORDERED").contains(order.getStatus())
                && (requester || admin || stockKeeper)) {
            return;
        }
        if ((Set.of("DRAFT", "PENDING_APPROVAL").contains(order.getStatus()) && (requester || admin))
                || ("APPROVED".equals(order.getStatus()) && (requester || admin || stockKeeper))
                || ("ORDERED".equals(order.getStatus()) && (admin || stockKeeper))) {
            return;
        }
        throw ProcurementApiException.operationDenied();
    }

    public void requirePolicyManagement() {
        if (!isSystemAdmin()) {
            throw ProcurementApiException.policyDenied();
        }
    }

    public Long userId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw ProcurementApiException.operationDenied();
        }
        return userId;
    }

    public boolean isSystemAdmin() {
        return UserContext.getRoles().contains("SYSTEM_ADMIN");
    }
}
