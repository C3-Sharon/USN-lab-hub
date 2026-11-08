package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.dto.procurement.ProcurementApprovalDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementCancelDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementMarkOrderedDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderCommandDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.entity.procurement.ProcurementOrderRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementPolicyRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementSubmitLineRecord;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderLineVO;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.mapper.SysUserMapper;
import com.usn.labhub.user.procurement.ProcurementAccessPolicy;
import com.usn.labhub.user.procurement.ProcurementApiException;
import com.usn.labhub.user.procurement.ProcurementSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProcurementWorkflowService {
    private static final Set<String> PLATFORMS = Set.of(
            "TAOBAO", "JD", "PINDUODUO", "1688", "OFFLINE", "OTHER");

    private final ProcurementMapper mapper;
    private final SysUserMapper userMapper;
    private final ProcurementAccessPolicy access;
    private final ProcurementSupport support;
    private final ProcurementOrderService orderService;

    public ProcurementWorkflowService(ProcurementMapper mapper, SysUserMapper userMapper,
                                      ProcurementAccessPolicy access, ProcurementSupport support,
                                      ProcurementOrderService orderService) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.access = access;
        this.support = support;
        this.orderService = orderService;
    }

    @Transactional
    public Map<String, Object> submit(Long id, String key, ProcurementOrderCommandDTO request) {
        return support.idempotent(operation(id, "submit"), key, request,
                () -> submitValue(id, request));
    }

    @Transactional
    public Map<String, Object> approve(Long id, String key, ProcurementApprovalDTO request) {
        return support.idempotent(operation(id, "approve"), key, request,
                () -> decide(id, request, true));
    }

    @Transactional
    public Map<String, Object> reject(Long id, String key, ProcurementApprovalDTO request) {
        return support.idempotent(operation(id, "reject"), key, request,
                () -> decide(id, request, false));
    }

    @Transactional
    public Map<String, Object> markOrdered(Long id, String key, ProcurementMarkOrderedDTO request) {
        access.requireOrderManagement();
        return support.idempotent(operation(id, "mark-ordered"), key, request,
                () -> markOrderedValue(id, request));
    }

    @Transactional
    public Map<String, Object> cancel(Long id, String key, ProcurementCancelDTO request) {
        return support.idempotent(operation(id, "cancel"), key, request,
                () -> cancelValue(id, request));
    }

    @Transactional
    public Map<String, Object> cancelRemaining(Long id, String key, ProcurementCancelDTO request) {
        access.requireOrderManagement();
        return support.idempotent(operation(id, "cancel-remaining"), key, request,
                () -> cancelRemainingValue(id, request));
    }

    @Transactional
    public Map<String, Object> complete(Long id, String key, ProcurementOrderCommandDTO request) {
        access.requireOrderManagement();
        return support.idempotent(operation(id, "complete"), key, request,
                () -> completeValue(id, request));
    }

    private Map<String, Object> submitValue(Long id, ProcurementOrderCommandDTO request) {
        ProcurementOrderRecord order = lockedVisible(id);
        access.requireSubmitOrCancel(order);
        requireVersion(order, request == null ? null : request.version());
        requireStatus(order, "DRAFT");

        ProcurementPolicyRecord policy = mapper.selectCurrentPolicyForUpdate();
        if (policy == null) {
            throw new IllegalStateException("Procurement policy is not initialized");
        }
        policy.setForcedCategoryIds(mapper.selectForcedCategoryIds(policy.getId()));
        List<ProcurementSubmitLineRecord> lines = mapper.selectSubmitLines(id);
        if (lines.isEmpty()) {
            throw ProcurementApiException.linesRequired();
        }

        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (order.getPayableAmount().compareTo(policy.getApprovalThreshold()) >= 0) {
            reasons.add("AMOUNT_THRESHOLD");
        }
        List<Map<String, Object>> valuableItems = new ArrayList<>();
        List<Map<String, Object>> forcedCategories = new ArrayList<>();
        for (ProcurementSubmitLineRecord line : lines) {
            if (!"ACTIVE".equals(line.getComponentStatus()) || line.getCurrentSpecSnapshotId() == null) {
                throw ProcurementApiException.componentInvalid();
            }
            if (line.getUnitPrice().compareTo(policy.getValuableUnitPriceThreshold()) >= 0) {
                reasons.add("VALUABLE_ITEM");
                valuableItems.add(Map.of("componentId", line.getComponentId(), "unitPrice", line.getUnitPrice()));
            }
            for (Long categoryId : policy.getForcedCategoryIds()) {
                if (line.getCategoryPath() != null && line.getCategoryPath().contains("/" + categoryId + "/")) {
                    reasons.add("FORCED_CATEGORY");
                    forcedCategories.add(Map.of("componentId", line.getComponentId(),
                            "categoryId", line.getCategoryId(), "matchedCategoryId", categoryId,
                            "categoryPath", line.getCategoryPath()));
                    break;
                }
            }
            mapper.freezeSubmitLine(line);
        }

        boolean approvalRequired = !reasons.isEmpty();
        List<String> responseReasons = approvalRequired ? List.copyOf(reasons) : List.of("EXEMPT");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", policy.getPolicyVersion());
        snapshot.put("payableAmount", order.getPayableAmount());
        snapshot.put("approvalThreshold", policy.getApprovalThreshold());
        snapshot.put("valuableUnitPriceThreshold", policy.getValuableUnitPriceThreshold());
        snapshot.put("reasons", responseReasons);
        snapshot.put("valuableItems", valuableItems);
        snapshot.put("forcedCategories", forcedCategories);

        String toStatus = approvalRequired ? "PENDING_APPROVAL" : "APPROVED";
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateSubmittedOrder(id, order.getVersion(), toStatus, approvalRequired,
                policy.getPolicyVersion(), support.json(responseReasons), support.json(snapshot),
                access.userId(), now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectOrder(id).getVersion());
        }
        Map<String, Object> approvalRecord = null;
        String requestId = UUID.randomUUID().toString();
        if (!approvalRequired) {
            mapper.insertApproval(id, "AUTO_APPROVED", policy.getPolicyVersion(), support.json(snapshot),
                    "SYSTEM", null, null, requestId, now);
            approvalRecord = Map.of("decision", "AUTO_APPROVED", "policyVersion", policy.getPolicyVersion());
        }
        support.audit(requestId, "SUBMIT", id, "DRAFT", toStatus, null,
                Map.of("policyVersion", policy.getPolicyVersion(), "reasons", responseReasons));
        Map<String, Object> result = orderResult(id);
        if (approvalRecord != null) {
            result.put("approvalRecord", approvalRecord);
        }
        return result;
    }

    private Map<String, Object> decide(Long id, ProcurementApprovalDTO request, boolean approved) {
        ProcurementOrderRecord order = lockedVisible(id);
        access.requireApprove(order);
        requireVersion(order, request == null ? null : request.version());
        requireStatus(order, "PENDING_APPROVAL");
        String comment = approved ? optionalText(request.comment(), 500) : requiredReason(request.comment());
        if (!StringUtils.hasText(order.getApprovalTriggerSnapshotJson()) || order.getApprovalPolicyVersion() == null) {
            throw new IllegalStateException("Submitted procurement snapshot is missing");
        }
        String decision = approved ? "APPROVED" : "REJECTED";
        String requestId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        mapper.insertApproval(id, decision, order.getApprovalPolicyVersion(),
                order.getApprovalTriggerSnapshotJson(), "USER", access.userId(), comment, requestId, now);
        if (mapper.updateDecisionState(id, order.getVersion(), "PENDING_APPROVAL", decision,
                access.userId(), now, approved ? null : comment, null) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectOrder(id).getVersion());
        }
        support.audit(requestId, approved ? "APPROVE" : "REJECT", id,
                "PENDING_APPROVAL", decision, comment,
                Map.of("policyVersion", order.getApprovalPolicyVersion()));
        return orderResult(id);
    }

    private Map<String, Object> markOrderedValue(Long id, ProcurementMarkOrderedDTO request) {
        ProcurementOrderRecord order = lockedVisible(id);
        requireVersion(order, request == null ? null : request.version());
        requireStatus(order, "APPROVED");
        if (request.buyerId() == null) {
            throw ProcurementApiException.invalid();
        }
        SysUser buyer = userMapper.selectById(request.buyerId());
        if (buyer == null || buyer.getStatus() == null || buyer.getStatus() != 1) {
            throw ProcurementApiException.invalid();
        }
        Long approver = mapper.selectLatestManualApprover(id);
        if (approver != null && approver.equals(request.buyerId())) {
            throw ProcurementApiException.dutyConflict();
        }
        String supplier = requiredText(request.supplierName(), 120, 2);
        String platform = enumValue(request.platformType(), PLATFORMS);
        String platformOrderNo = optionalText(request.platformOrderNo(), 120);
        BigDecimal actualPaid = money(request.actualPaidAmount());
        if (actualPaid.compareTo(order.getPayableAmount()) > 0) {
            throw ProcurementApiException.amountInvalid();
        }
        String differenceNote = optionalText(request.paymentDifferenceNote(), 500);
        if (actualPaid.compareTo(order.getPayableAmount()) < 0 && !StringUtils.hasText(differenceNote)) {
            throw ProcurementApiException.amountInvalid();
        }
        allocateCosts(id, actualPaid);
        LocalDateTime now = LocalDateTime.now();
        try {
            if (mapper.updateOrderedState(id, order.getVersion(), request.buyerId(), buyer.getUsername(),
                    supplier, platform, platformOrderNo, normalizeOrderNo(platformOrderNo),
                    actualPaid, differenceNote, access.userId(), now) != 1) {
                throw ProcurementApiException.versionConflict(mapper.selectOrder(id).getVersion());
            }
        } catch (DuplicateKeyException exception) {
            throw ProcurementApiException.platformOrderDuplicate();
        }
        String requestId = UUID.randomUUID().toString();
        support.audit(requestId, "MARK_ORDERED", id, "APPROVED", "ORDERED", null,
                Map.of("buyerId", request.buyerId(), "actualPaidAmount", actualPaid));
        return orderResult(id);
    }

    private Map<String, Object> cancelValue(Long id, ProcurementCancelDTO request) {
        ProcurementOrderRecord order = lockedVisible(id);
        access.requireCancel(order);
        requireVersion(order, request == null ? null : request.version());
        String reason = requiredReason(request.reason());
        if (!Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "ORDERED").contains(order.getStatus())) {
            throw ProcurementApiException.invalidTransition();
        }
        if ("ORDERED".equals(order.getStatus()) && mapper.countConfirmedReceipts(id) > 0) {
            throw ProcurementApiException.invalidTransition();
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateDecisionState(id, order.getVersion(), order.getStatus(), "CANCELED",
                access.userId(), now, reason, null) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectOrder(id).getVersion());
        }
        support.audit(UUID.randomUUID().toString(), "CANCEL", id, order.getStatus(), "CANCELED", reason, null);
        return orderResult(id);
    }

    private Map<String, Object> cancelRemainingValue(Long id, ProcurementCancelDTO request) {
        ProcurementOrderRecord order = lockedVisible(id);
        requireVersion(order, request == null ? null : request.version());
        requireStatus(order, "PARTIALLY_RECEIVED");
        String reason = requiredReason(request.reason());
        mapper.cancelRemainingLines(id);
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateDecisionState(id, order.getVersion(), "PARTIALLY_RECEIVED", "COMPLETED",
                access.userId(), now, "REMAINDER_CANCELED: " + reason, now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectOrder(id).getVersion());
        }
        support.audit(UUID.randomUUID().toString(), "CANCEL_REMAINING", id,
                "PARTIALLY_RECEIVED", "COMPLETED", reason, null);
        return orderResult(id);
    }

    private Map<String, Object> completeValue(Long id, ProcurementOrderCommandDTO request) {
        ProcurementOrderRecord order = lockedVisible(id);
        requireVersion(order, request == null ? null : request.version());
        requireStatus(order, "RECEIVED");
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateDecisionState(id, order.getVersion(), "RECEIVED", "COMPLETED",
                access.userId(), now, null, now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectOrder(id).getVersion());
        }
        support.audit(UUID.randomUUID().toString(), "COMPLETE", id, "RECEIVED", "COMPLETED", null, null);
        return orderResult(id);
    }

    private void allocateCosts(Long orderId, BigDecimal actualPaid) {
        List<ProcurementOrderLineVO> lines = mapper.selectOrderLines(orderId);
        BigDecimal subtotal = lines.stream().map(ProcurementOrderLineVO::getLineSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lines.isEmpty() || subtotal.signum() <= 0) {
            throw ProcurementApiException.amountInvalid();
        }
        BigDecimal allocated = BigDecimal.ZERO.setScale(2);
        for (int index = 0; index < lines.size(); index++) {
            ProcurementOrderLineVO line = lines.get(index);
            BigDecimal lineAmount = index == lines.size() - 1
                    ? actualPaid.subtract(allocated).setScale(2, RoundingMode.UNNECESSARY)
                    : actualPaid.multiply(line.getLineSubtotal()).divide(subtotal, 2, RoundingMode.HALF_UP);
            allocated = allocated.add(lineAmount);
            BigDecimal unitCost = lineAmount.divide(line.getOrderedQuantity(), 4, RoundingMode.HALF_UP);
            mapper.updateLineAllocation(line.getId(), lineAmount, unitCost);
        }
    }

    private ProcurementOrderRecord lockedVisible(Long id) {
        ProcurementOrderRecord order = mapper.selectOrderForUpdate(id);
        if (order == null || !access.canView(order)) {
            throw ProcurementApiException.orderNotFound();
        }
        return order;
    }

    private Map<String, Object> orderResult(Long id) {
        return new LinkedHashMap<>(support.asMap(orderService.order(id)));
    }

    private void requireVersion(ProcurementOrderRecord order, Integer version) {
        if (version == null) {
            throw ProcurementApiException.invalid();
        }
        if (!order.getVersion().equals(version)) {
            throw ProcurementApiException.versionConflict(order.getVersion());
        }
    }

    private void requireStatus(ProcurementOrderRecord order, String status) {
        if (!status.equals(order.getStatus())) {
            throw ProcurementApiException.invalidTransition();
        }
    }

    private String operation(Long id, String action) {
        return "POST:/api/procurement-orders/" + id + "/" + action;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || Math.max(0, value.stripTrailingZeros().scale()) > 2) {
            throw ProcurementApiException.amountInvalid();
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String requiredReason(String value) {
        String normalized = optionalText(value, 500);
        if (!StringUtils.hasText(normalized)) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }

    private String requiredText(String value, int max, int min) {
        String normalized = optionalText(value, max);
        if (normalized == null || normalized.length() < min) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }

    private String optionalText(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }

    private String enumValue(String value, Set<String> values) {
        if (!StringUtils.hasText(value)) {
            throw ProcurementApiException.invalid();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }

    private String normalizeOrderNo(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
