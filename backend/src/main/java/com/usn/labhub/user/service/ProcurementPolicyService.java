package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.dto.procurement.ProcurementPolicyUpdateDTO;
import com.usn.labhub.user.domain.entity.procurement.ProcurementPolicyRecord;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.procurement.ProcurementAccessPolicy;
import com.usn.labhub.user.procurement.ProcurementApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ProcurementPolicyService {
    private final ProcurementMapper mapper;
    private final ProcurementAccessPolicy access;

    public ProcurementPolicyService(ProcurementMapper mapper, ProcurementAccessPolicy access) {
        this.mapper = mapper;
        this.access = access;
    }

    public ProcurementPolicyRecord current() {
        access.requireCreate();
        return decorate(requiredCurrent());
    }

    public List<ProcurementPolicyRecord> versions() {
        access.requireCreate();
        List<ProcurementPolicyRecord> versions = mapper.selectPolicyVersions();
        versions.forEach(this::decorate);
        return versions;
    }

    @Transactional
    public ProcurementPolicyRecord publish(ProcurementPolicyUpdateDTO request) {
        access.requirePolicyManagement();
        if (request == null || request.currentPolicyVersion() == null) {
            throw ProcurementApiException.invalid();
        }
        BigDecimal approvalThreshold = positive(request.approvalThreshold(), 2);
        BigDecimal valuableThreshold = positive(request.valuableUnitPriceThreshold(), 4);
        List<Long> categories = categories(request.forcedCategoryIds());
        ProcurementPolicyRecord current = mapper.selectCurrentPolicyForUpdate();
        if (current == null || !current.getPolicyVersion().equals(request.currentPolicyVersion())) {
            throw ProcurementApiException.policyChanged(current == null ? 0 : current.getPolicyVersion());
        }
        if (!categories.isEmpty() && mapper.countActiveCategories(categories) != categories.size()) {
            throw ProcurementApiException.invalid();
        }
        LocalDateTime now = LocalDateTime.now();
        ProcurementPolicyRecord next = new ProcurementPolicyRecord();
        next.setPolicyVersion(current.getPolicyVersion() + 1);
        next.setCurrency("CNY");
        next.setApprovalThreshold(approvalThreshold);
        next.setValuableUnitPriceThreshold(valuableThreshold);
        next.setEffectiveAt(now);
        next.setCreatedBy(access.userId());
        next.setCreateTime(now);
        mapper.insertPolicy(next);
        categories.forEach(categoryId -> mapper.insertForcedCategory(next.getId(), categoryId));
        return decorate(next);
    }

    private ProcurementPolicyRecord requiredCurrent() {
        ProcurementPolicyRecord current = mapper.selectCurrentPolicy();
        if (current == null) {
            throw new IllegalStateException("Procurement policy is not initialized");
        }
        return current;
    }

    private ProcurementPolicyRecord decorate(ProcurementPolicyRecord policy) {
        policy.setForcedCategoryIds(mapper.selectForcedCategoryIds(policy.getId()));
        return policy;
    }

    private BigDecimal positive(BigDecimal value, int scale) {
        if (value == null || value.signum() <= 0 || Math.max(0, value.stripTrailingZeros().scale()) > scale) {
            throw ProcurementApiException.invalid();
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }

    private List<Long> categories(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > 100 || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw ProcurementApiException.invalid();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }
}
