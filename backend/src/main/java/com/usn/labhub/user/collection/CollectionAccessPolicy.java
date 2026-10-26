package com.usn.labhub.user.collection;

import com.usn.labhub.user.domain.entity.collection.CollectionBatchRow;
import com.usn.labhub.user.domain.entity.collection.CollectionPlanRow;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CollectionAccessPolicy {
    private static final Set<String> MANAGERS = Set.of("OWNER", "MAINTAINER");
    private static final Set<String> PROJECT_WRITABLE = Set.of("PREPARING", "ACTIVE");
    private final CollectionSupport support;

    public CollectionAccessPolicy(CollectionSupport support) {
        this.support = support;
    }

    public void requireProjectRead(CollectionPlanRow context) {
        if (context == null || context.getProjectId() == null
                || context.getActorProjectRole() == null && !support.globalReader()) {
            throw CollectionApiException.projectNotFound();
        }
    }

    public void requirePlanRead(CollectionPlanRow plan) {
        if (plan == null || plan.getActorProjectRole() == null && !support.globalReader()) {
            throw CollectionApiException.planNotFound();
        }
    }

    public void requireProjectManage(CollectionPlanRow context) {
        requireProjectRead(context);
        requireWritableProject(context.getProjectStatus());
        if (!support.systemAdmin() && !MANAGERS.contains(context.getActorProjectRole())) {
            throw CollectionApiException.denied();
        }
    }

    public void requireBatchRead(CollectionBatchRow batch) {
        if (batch == null || batch.getActorProjectRole() == null && !support.globalReader()) {
            throw CollectionApiException.batchNotFound();
        }
    }

    public void requireManage(CollectionPlanRow plan) {
        requirePlanRead(plan);
        requireWritableProject(plan.getProjectStatus());
        if (!support.systemAdmin() && !MANAGERS.contains(plan.getActorProjectRole())) {
            throw CollectionApiException.denied();
        }
    }

    public void requireExecute(CollectionPlanRow plan) {
        requirePlanRead(plan);
        requireWritableProject(plan.getProjectStatus());
        Long actorId = support.requiredUserId();
        if (!support.systemAdmin() && !MANAGERS.contains(plan.getActorProjectRole())
                && !(actorId.equals(plan.getOwnerId()) && "MEMBER".equals(plan.getActorProjectRole()))) {
            throw CollectionApiException.denied();
        }
    }

    public void requireExecute(CollectionBatchRow batch) {
        requireBatchRead(batch);
        requireWritableProject(batch.getProjectStatus());
        Long actorId = support.requiredUserId();
        if (!support.systemAdmin() && !MANAGERS.contains(batch.getActorProjectRole())
                && !(actorId.equals(batch.getPlanOwnerId()) && "MEMBER".equals(batch.getActorProjectRole()))) {
            throw CollectionApiException.denied();
        }
    }

    public void requireWritableProject(String status) {
        if (!PROJECT_WRITABLE.contains(status)) {
            throw CollectionApiException.projectState(status == null ? "ARCHIVED" : status);
        }
    }
}
