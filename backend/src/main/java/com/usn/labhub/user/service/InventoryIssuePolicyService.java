package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.dto.inventory.InventoryIssuePolicyOverrideDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssuePolicyUpdateDTO;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueComponentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyOverrideRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyRecord;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePolicyVO;
import com.usn.labhub.user.inventory.InventoryIssueAccessPolicy;
import com.usn.labhub.user.inventory.InventoryIssueApiException;
import com.usn.labhub.user.mapper.InventoryIssueMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class InventoryIssuePolicyService {
    private final InventoryIssueMapper mapper;
    private final InventoryIssueAccessPolicy access;

    public InventoryIssuePolicyService(InventoryIssueMapper mapper, InventoryIssueAccessPolicy access) {
        this.mapper = mapper;
        this.access = access;
    }

    public InventoryIssuePolicyVO current() {
        access.requireCreate();
        InventoryIssuePolicyRecord current = mapper.selectCurrentPolicy();
        return current == null ? defaultPolicy() : decorate(current);
    }

    public List<InventoryIssuePolicyVO> versions() {
        access.requireCreate();
        return mapper.selectPolicyVersions().stream().map(this::decorate).toList();
    }

    @Transactional
    public InventoryIssuePolicyVO publish(InventoryIssuePolicyUpdateDTO request) {
        access.requirePolicyManagement();
        if (request == null || request.basePolicyVersion() == null || request.basePolicyVersion() < 0) {
            throw InventoryIssueApiException.invalid();
        }
        BigDecimal directAmount = positive(request.directAmountThreshold(), 2);
        BigDecimal valuable = positive(request.valuableUnitCostThreshold(), 4);
        BigDecimal quantityLimit = positive(request.defaultDirectQuantityLimit(), 3);
        BigDecimal lowStock = ratio(request.lowStockRatio());
        int ttl = ttl(request.reservationTtlHours());
        List<Long> categories = categories(request.forcedCategoryIds());
        List<InventoryIssuePolicyOverrideRecord> overrides = overrides(request.componentOverrides());

        InventoryIssuePolicyRecord current = mapper.selectCurrentPolicyForUpdate();
        int currentVersion = current == null ? 0 : current.getPolicyVersion();
        if (currentVersion != request.basePolicyVersion()) {
            throw InventoryIssueApiException.versionConflict(currentVersion);
        }
        if (!categories.isEmpty() && mapper.countActiveCategories(categories) != categories.size()) {
            throw InventoryIssueApiException.policyInvalid();
        }

        LocalDateTime now = LocalDateTime.now();
        InventoryIssuePolicyRecord next = new InventoryIssuePolicyRecord();
        next.setPolicyVersion(currentVersion + 1);
        next.setCurrency("CNY");
        next.setDirectAmountThreshold(directAmount);
        next.setValuableUnitCostThreshold(valuable);
        next.setDefaultDirectQuantityLimit(quantityLimit);
        next.setLowStockRatio(lowStock);
        next.setReservationTtlHours(ttl);
        next.setEffectiveAt(now);
        next.setCreatedBy(access.userId());
        next.setCreateTime(now);
        mapper.insertPolicy(next);
        categories.forEach(categoryId -> mapper.insertForcedCategory(next.getId(), categoryId));
        overrides.forEach(override -> {
            override.setPolicyId(next.getId());
            override.setCreatedBy(access.userId());
            override.setCreateTime(now);
            mapper.insertOverride(override);
        });
        return decorate(next);
    }

    private InventoryIssuePolicyVO decorate(InventoryIssuePolicyRecord policy) {
        InventoryIssuePolicyVO view = new InventoryIssuePolicyVO();
        view.setId(policy.getId());
        view.setPolicyVersion(policy.getPolicyVersion());
        view.setCurrency(policy.getCurrency());
        view.setDirectAmountThreshold(policy.getDirectAmountThreshold());
        view.setValuableUnitCostThreshold(policy.getValuableUnitCostThreshold());
        view.setDefaultDirectQuantityLimit(policy.getDefaultDirectQuantityLimit());
        view.setLowStockRatio(policy.getLowStockRatio());
        view.setReservationTtlHours(policy.getReservationTtlHours());
        view.setForcedCategoryIds(mapper.selectForcedCategoryIds(policy.getId()));
        view.setComponentOverrides(mapper.selectOverrides(policy.getId()));
        view.setEffectiveAt(policy.getEffectiveAt());
        view.setCreatedBy(policy.getCreatedBy());
        view.setCreateTime(policy.getCreateTime());
        return view;
    }

    private InventoryIssuePolicyVO defaultPolicy() {
        InventoryIssuePolicyVO view = new InventoryIssuePolicyVO();
        view.setPolicyVersion(0);
        view.setCurrency("CNY");
        view.setDirectAmountThreshold(new BigDecimal("100.00"));
        view.setValuableUnitCostThreshold(new BigDecimal("500.0000"));
        view.setDefaultDirectQuantityLimit(new BigDecimal("10.000"));
        view.setLowStockRatio(new BigDecimal("20.00"));
        view.setReservationTtlHours(72);
        view.setForcedCategoryIds(List.of());
        view.setComponentOverrides(List.of());
        return view;
    }

    private List<InventoryIssuePolicyOverrideRecord> overrides(
            List<InventoryIssuePolicyOverrideDTO> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() > 200) {
            throw InventoryIssueApiException.policyOverrideInvalid();
        }
        Set<Long> seen = new HashSet<>();
        List<InventoryIssuePolicyOverrideRecord> result = new ArrayList<>();
        for (InventoryIssuePolicyOverrideDTO request : requests) {
            if (request == null || request.componentId() == null || !seen.add(request.componentId())) {
                throw InventoryIssueApiException.policyOverrideInvalid();
            }
            BigDecimal limit = overridePositive(request.directQuantityLimit(), 3);
            InventoryIssueComponentRecord component = mapper.selectComponentForIssue(request.componentId());
            if (component == null || !"ACTIVE".equals(component.getStatus())) {
                throw InventoryIssueApiException.policyOverrideInvalid();
            }
            InventoryIssuePolicyOverrideRecord override = new InventoryIssuePolicyOverrideRecord();
            override.setComponentId(component.getId());
            override.setDirectQuantityLimit(limit);
            override.setComponentName(component.getName());
            override.setUnitSnapshot(component.getBaseUnit());
            result.add(override);
        }
        return result;
    }

    private List<Long> categories(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > 100 || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw InventoryIssueApiException.policyInvalid();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private BigDecimal positive(BigDecimal value, int scale) {
        if (value == null || value.signum() <= 0 || precisionScale(value) > scale) {
            throw InventoryIssueApiException.policyInvalid();
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }

    private BigDecimal overridePositive(BigDecimal value, int scale) {
        if (value == null || value.signum() <= 0 || precisionScale(value) > scale) {
            throw InventoryIssueApiException.policyOverrideInvalid();
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }

    private BigDecimal ratio(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0
                || precisionScale(value) > 2) {
            throw InventoryIssueApiException.policyInvalid();
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private int ttl(Integer value) {
        if (value == null || value < 1 || value > 168) {
            throw InventoryIssueApiException.policyInvalid();
        }
        return value;
    }

    private int precisionScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }
}
