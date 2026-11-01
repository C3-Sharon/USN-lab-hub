package com.usn.labhub.user.component;

import com.usn.labhub.user.common.utils.UserContext;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ComponentCatalogAccessPolicy {
    private static final Set<String> CATALOG_ROLES = Set.of(
            "SYSTEM_ADMIN", "STOCK_KEEPER", "TEACHER", "MEMBER");

    public void requireAccess() {
        if (UserContext.getRoles().stream().noneMatch(CATALOG_ROLES::contains)) {
            throw ComponentCatalogApiException.operationDenied();
        }
    }

    public void requireManage() {
        if (!canManage()) {
            throw ComponentCatalogApiException.operationDenied();
        }
    }

    public void requireSpecManage() {
        if (!canManage()) {
            throw ComponentCatalogApiException.specConfirmDenied();
        }
    }

    public void requireMediaManage() {
        if (!canManage()) {
            throw ComponentCatalogApiException.mediaOperationDenied();
        }
    }

    public void requireCandidateRead() {
        if (!canReadAllStates()) {
            throw ComponentCatalogApiException.candidateNotFound();
        }
    }

    public boolean canManage() {
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("STOCK_KEEPER");
    }

    public boolean canReadAllStates() {
        return canManage() || UserContext.getRoles().contains("TEACHER");
    }
}
