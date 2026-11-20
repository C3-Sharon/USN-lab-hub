package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.dto.inventory.InventoryIssueCommandDTO;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueApprovalRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueBatchRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueComponentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationAllocationRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryBalanceRecord;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePolicyOverrideVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.inventory.InventoryIssueAccessPolicy;
import com.usn.labhub.user.inventory.InventoryIssueApiException;
import com.usn.labhub.user.inventory.InventoryIssueSupport;
import com.usn.labhub.user.mapper.InventoryIssueMapper;
import com.usn.labhub.user.mapper.ProcurementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryIssueWorkflowService {
    private static final BigDecimal DEFAULT_AMOUNT_THRESHOLD = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_VALUABLE_UNIT_COST = new BigDecimal("500.0000");
    private static final BigDecimal DEFAULT_QUANTITY_LIMIT = new BigDecimal("10.000");
    private static final BigDecimal DEFAULT_LOW_STOCK_RATIO = new BigDecimal("20.00");
    private static final int DEFAULT_TTL_HOURS = 72;

    private final InventoryIssueMapper mapper;
    private final ProcurementMapper procurementMapper;
    private final InventoryIssueAccessPolicy access;
    private final InventoryIssueSupport support;
    private final InventoryIssueRequestService requestService;
    private final ObjectMapper objectMapper;
    private final InventoryIssueReleaseSupport releaseSupport;
    private final InventoryIssueExpiryService expiryService;

    public InventoryIssueWorkflowService(InventoryIssueMapper mapper,
                                         ProcurementMapper procurementMapper,
                                         InventoryIssueAccessPolicy access,
                                         InventoryIssueSupport support,
                                         InventoryIssueRequestService requestService,
                                         ObjectMapper objectMapper,
                                         InventoryIssueReleaseSupport releaseSupport,
                                         InventoryIssueExpiryService expiryService) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
        this.access = access;
        this.support = support;
        this.requestService = requestService;
        this.objectMapper = objectMapper;
        this.releaseSupport = releaseSupport;
        this.expiryService = expiryService;
    }

    @Transactional
    public Map<String, Object> submit(Long id, String key, InventoryIssueCommandDTO request) {
        return support.idempotent(operation(id, "submit"), key, request, () -> submitValue(id, request));
    }

    @Transactional
    public Map<String, Object> approve(Long id, String key, InventoryIssueCommandDTO request) {
        expiryService.expireIfNeeded(id);
        return support.idempotent(operation(id, "approve"), key, request, () -> decide(id, request, true));
    }

    @Transactional
    public Map<String, Object> reject(Long id, String key, InventoryIssueCommandDTO request) {
        expiryService.expireIfNeeded(id);
        return support.idempotent(operation(id, "reject"), key, request, () -> decide(id, request, false));
    }

    @Transactional
    public Map<String, Object> cancel(Long id, String key, InventoryIssueCommandDTO request) {
        expiryService.expireIfNeeded(id);
        return support.idempotent(operation(id, "cancel"), key, request, () -> cancelValue(id, request));
    }

    private Map<String, Object> submitValue(Long id, InventoryIssueCommandDTO request) {
        InventoryIssueRequestRecord issue = lockedVisible(id);
        access.requireSubmitOrCancel(issue);
        requireVersion(issue, request == null ? null : request.requestVersion());
        requireStatus(issue, "DRAFT");

        List<InventoryIssueLineRecord> lines = mapper.selectLinesForUpdate(id);
        if (lines.isEmpty()) {
            throw InventoryIssueApiException.linesRequired();
        }
        Policy policy = loadPolicy();
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();

        List<Long> componentIds = lines.stream().map(InventoryIssueLineRecord::getComponentId)
                .distinct().sorted().toList();
        List<InventoryBalanceRecord> balances = procurementMapper
                .selectInventoryBalancesForUpdate(componentIds);
        Map<Long, InventoryBalanceRecord> balanceByComponent = balances.stream()
                .collect(Collectors.toMap(InventoryBalanceRecord::getComponentId, Function.identity()));
        for (Long componentId : componentIds) {
            if (!balanceByComponent.containsKey(componentId)) {
                BigDecimal requested = requestedFor(lines, componentId);
                throw InventoryIssueApiException.insufficientStock(componentId, requested, BigDecimal.ZERO);
            }
        }

        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        List<Map<String, Object>> valuableBatches = new ArrayList<>();
        List<Map<String, Object>> forcedCategories = new ArrayList<>();
        List<Map<String, Object>> overridesHit = new ArrayList<>();
        List<LineReservation> lineReservations = new ArrayList<>();
        Map<Long, BigDecimal> reservedDeltaByComponent = new HashMap<>();
        Map<Long, List<InventoryIssueBatchRecord>> batchesByComponent = new LinkedHashMap<>();
        for (Long componentId : componentIds) {
            batchesByComponent.put(componentId,
                    sortedBatches(mapper.selectBatchesForUpdate(componentId)));
        }

        for (InventoryIssueLineRecord line : lines) {
            InventoryIssueComponentRecord component = mapper.selectComponentForIssue(line.getComponentId());
            if (component == null || !"ACTIVE".equals(component.getStatus())) {
                throw InventoryIssueApiException.componentInvalid();
            }
            BigDecimal limit = policy.quantityLimit(component.getId());
            if (line.getRequestedQuantity().compareTo(limit) > 0) {
                reasons.add("QUANTITY_THRESHOLD");
                overridesHit.add(Map.of("componentId", component.getId(), "quantityLimit", limit));
            }
            for (Long categoryId : policy.forcedCategoryIds()) {
                if (component.getCategoryPath() != null
                        && component.getCategoryPath().contains("/" + categoryId + "/")) {
                    reasons.add("FORCED_CATEGORY");
                    forcedCategories.add(Map.of("componentId", component.getId(),
                            "matchedCategoryId", categoryId, "categoryPath", component.getCategoryPath()));
                    break;
                }
            }

            List<InventoryIssueBatchRecord> batches = batchesByComponent.get(component.getId());
            BigDecimal remaining = line.getRequestedQuantity();
            List<Map<String, Object>> allocations = new ArrayList<>();
            for (InventoryIssueBatchRecord batch : batches) {
                if (remaining.signum() == 0) {
                    break;
                }
                BigDecimal available = batch.getCurrentQuantity().subtract(batch.getReservedQuantity());
                BigDecimal take = remaining.min(available);
                if (take.signum() <= 0) {
                    continue;
                }
                remaining = remaining.subtract(take);
                if (batch.getUnitCost().compareTo(policy.valuableUnitCost()) >= 0) {
                    reasons.add("VALUABLE_BATCH");
                    valuableBatches.add(Map.of("componentId", component.getId(),
                            "batchId", batch.getId(), "unitCost", batch.getUnitCost()));
                }
                allocations.add(Map.of("batchId", batch.getId(), "quantity", take,
                        "unitCostSnapshot", batch.getUnitCost()));
            }
            if (remaining.signum() > 0) {
                BigDecimal available = batches.stream()
                        .map(batch -> batch.getCurrentQuantity().subtract(batch.getReservedQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                throw InventoryIssueApiException.insufficientStock(component.getId(),
                        line.getRequestedQuantity(), available);
            }
            lineReservations.add(new LineReservation(line, allocations));
            reservedDeltaByComponent.merge(component.getId(), line.getRequestedQuantity(), BigDecimal::add);
        }

        BigDecimal estimatedAmount = estimatedAmount(lineReservations);
        evaluateLowStock(reservedDeltaByComponent, balanceByComponent, policy, reasons);

        boolean approvalRequired = !reasons.isEmpty();
        List<String> responseReasons = approvalRequired ? List.copyOf(reasons) : List.of("EXEMPT");
        String status = approvalRequired ? "PENDING_APPROVAL" : "APPROVED";
        LocalDateTime expiresAt = dbNow.plusHours(policy.ttlHours());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", policy.version());
        snapshot.put("estimatedAmount", estimatedAmount);
        snapshot.put("reasons", responseReasons);
        snapshot.put("valuableBatches", valuableBatches);
        snapshot.put("forcedCategories", forcedCategories);
        snapshot.put("overridesHit", overridesHit);
        snapshot.put("expiresAt", expiresAt.toString());

        if (mapper.submitRequest(id, issue.getVersion(), status, approvalRequired,
                support.json(responseReasons), policy.version(), estimatedAmount,
                dbNow, expiresAt, access.userId(), dbNow) != 1) {
            throw InventoryIssueApiException.versionConflict(latestVersion(id));
        }

        reserveBalances(reservedDeltaByComponent, dbNow);
        createReservations(id, lineReservations, dbNow, expiresAt);
        if (!approvalRequired) {
            insertApproval(id, "AUTO_APPROVED", "SYSTEM", null, policy.version(),
                    support.json(snapshot), null, dbNow);
        }
        support.audit(UUID.randomUUID().toString(), "SUBMIT", "INVENTORY_ISSUE_REQUEST", id,
                "DRAFT", status, null, Map.of("policyVersion", policy.version(), "reasons", responseReasons));

        Map<String, Object> result = support.asMap(requestService.detail(id));
        result.put("reservationVersion", 1);
        result.put("lines", lineSummaries(reservedDeltaByComponent, balanceByComponent));
        return result;
    }

    private Map<String, Object> decide(Long id, InventoryIssueCommandDTO request, boolean approved) {
        InventoryIssueRequestRecord issue = lockedVisible(id);
        access.requireApprove(issue);
        requireVersion(issue, request == null ? null : request.requestVersion());
        if ("EXPIRED".equals(issue.getStatus())) {
            throw InventoryIssueApiException.reservationExpired();
        }
        requireStatus(issue, "PENDING_APPROVAL");

        String comment = approved ? optionalText(request == null ? null : request.comment(), 500)
                : requiredText(request == null ? null : request.comment(), 500);
        String decision = approved ? "APPROVED" : "REJECTED";
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", issue.getPolicyVersion());
        snapshot.put("estimatedAmount", issue.getEstimatedAmount());
        snapshot.put("approvalReasons", parseReasons(issue.getApprovalReasonsJson()));
        insertApproval(id, decision, "USER", access.userId(), issue.getPolicyVersion(),
                support.json(snapshot), comment, dbNow);

        if (approved) {
            if (mapper.decideRequest(id, issue.getVersion(), "PENDING_APPROVAL", "APPROVED",
                    dbNow, access.userId(), dbNow) != 1) {
                throw InventoryIssueApiException.versionConflict(latestVersion(id));
            }
        } else {
            releaseReservations(issue, "REJECTED", dbNow);
            if (mapper.decideRequest(id, issue.getVersion(), "PENDING_APPROVAL", "REJECTED",
                    null, access.userId(), dbNow) != 1) {
                throw InventoryIssueApiException.versionConflict(latestVersion(id));
            }
        }
        support.audit(UUID.randomUUID().toString(), approved ? "APPROVE" : "REJECT",
                "INVENTORY_ISSUE_REQUEST", id, "PENDING_APPROVAL", decision, comment,
                Map.of("policyVersion", issue.getPolicyVersion()));
        return support.asMap(requestService.detail(id));
    }

    private Map<String, Object> cancelValue(Long id, InventoryIssueCommandDTO request) {
        InventoryIssueRequestRecord issue = lockedVisible(id);
        access.requireSubmitOrCancel(issue);
        requireVersion(issue, request == null ? null : request.requestVersion());
        if (!Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED").contains(issue.getStatus())) {
            if ("EXPIRED".equals(issue.getStatus())) {
                throw InventoryIssueApiException.reservationExpired();
            }
            throw InventoryIssueApiException.invalidTransition();
        }
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();
        if (!"DRAFT".equals(issue.getStatus())) {
            releaseReservations(issue, "CANCELED", dbNow);
        }
        if (mapper.cancelRequest(id, issue.getVersion(), issue.getStatus(),
                access.userId(), dbNow) != 1) {
            throw InventoryIssueApiException.versionConflict(latestVersion(id));
        }
        support.audit(UUID.randomUUID().toString(), "CANCEL", "INVENTORY_ISSUE_REQUEST", id,
                issue.getStatus(), "CANCELED", null, null);
        return support.asMap(requestService.detail(id));
    }

    private void reserveBalances(Map<Long, BigDecimal> deltas, LocalDateTime now) {
        for (Map.Entry<Long, BigDecimal> entry : deltas.entrySet()) {
            if (mapper.reserveBalance(entry.getKey(), entry.getValue(), now) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
        }
    }

    private void createReservations(Long requestId, List<LineReservation> lineReservations,
                                    LocalDateTime now, LocalDateTime expiresAt) {
        for (LineReservation lineReservation : lineReservations) {
            InventoryIssueLineRecord line = lineReservation.line();
            InventoryIssueReservationRecord reservation = new InventoryIssueReservationRecord();
            reservation.setReservationNo(reservationNo(now.toLocalDate()));
            reservation.setRequestId(requestId);
            reservation.setRequestLineId(line.getId());
            reservation.setComponentId(line.getComponentId());
            reservation.setQuantity(line.getRequestedQuantity());
            reservation.setExpiresAt(expiresAt);
            mapper.insertReservation(reservation);
            if (mapper.reserveLine(line.getId(), line.getRequestedQuantity()) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
            for (Map<String, Object> allocation : lineReservation.allocations()) {
                Long batchId = ((Number) allocation.get("batchId")).longValue();
                BigDecimal quantity = (BigDecimal) allocation.get("quantity");
                BigDecimal unitCost = (BigDecimal) allocation.get("unitCostSnapshot");
                InventoryIssueReservationAllocationRecord record =
                        new InventoryIssueReservationAllocationRecord();
                record.setReservationId(reservation.getId());
                record.setBatchId(batchId);
                record.setQuantity(quantity);
                record.setUnitCostSnapshot(unitCost);
                mapper.insertReservationAllocation(record);
                if (mapper.reserveBatch(batchId, quantity) != 1) {
                    throw InventoryIssueApiException.concurrentConflict();
                }
            }
        }
    }

    private void releaseReservations(InventoryIssueRequestRecord issue, String releaseReason,
                                     LocalDateTime now) {
        releaseSupport.releaseLocked(issue, releaseReason, now);
    }

    private void insertApproval(Long requestId, String decision, String actorType, Long decidedBy,
                                Integer policyVersion, String snapshot, String comment,
                                LocalDateTime decidedAt) {
        InventoryIssueApprovalRecord approval = new InventoryIssueApprovalRecord();
        approval.setRequestId(requestId);
        approval.setDecision(decision);
        approval.setActorType(actorType);
        approval.setDecidedBy(decidedBy);
        approval.setDecidedAt(decidedAt);
        approval.setPolicyVersion(policyVersion == null ? 0 : policyVersion);
        approval.setTriggerSnapshotJson(snapshot);
        approval.setComment(comment);
        mapper.insertApproval(approval);
    }

    private void evaluateLowStock(Map<Long, BigDecimal> deltas,
                                  Map<Long, InventoryBalanceRecord> balanceByComponent,
                                  Policy policy, LinkedHashSet<String> reasons) {
        BigDecimal ratio = policy.lowStockRatio().movePointLeft(2);
        for (Map.Entry<Long, BigDecimal> entry : deltas.entrySet()) {
            InventoryBalanceRecord balance = balanceByComponent.get(entry.getKey());
            BigDecimal onHand = balance.getOnHandQuantity();
            BigDecimal reservedAfter = balance.getReservedQuantity().add(entry.getValue());
            if (onHand.signum() == 0) {
                throw InventoryIssueApiException.insufficientStock(entry.getKey(),
                        entry.getValue(), BigDecimal.ZERO);
            }
            BigDecimal availableAfter = onHand.subtract(reservedAfter);
            if (availableAfter.divide(onHand, 4, RoundingMode.HALF_UP).compareTo(ratio) < 0) {
                reasons.add("LOW_STOCK");
            }
        }
    }

    private List<Map<String, Object>> lineSummaries(Map<Long, BigDecimal> deltas,
                                                    Map<Long, InventoryBalanceRecord> balanceByComponent) {
        List<Map<String, Object>> lines = new ArrayList<>();
        deltas.keySet().stream().sorted().forEach(componentId -> {
            InventoryBalanceRecord balance = balanceByComponent.get(componentId);
            BigDecimal onHand = balance.getOnHandQuantity();
            BigDecimal reserved = balance.getReservedQuantity().add(deltas.get(componentId));
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("componentId", componentId);
            line.put("onHandQuantity", onHand);
            line.put("reservedQuantity", reserved);
            line.put("availableQuantity", onHand.subtract(reserved));
            lines.add(line);
        });
        return lines;
    }

    private Policy loadPolicy() {
        InventoryIssuePolicyRecord policy = mapper.selectCurrentPolicy();
        if (policy == null) {
            return new Policy(0, DEFAULT_AMOUNT_THRESHOLD, DEFAULT_VALUABLE_UNIT_COST,
                    DEFAULT_QUANTITY_LIMIT, DEFAULT_LOW_STOCK_RATIO, DEFAULT_TTL_HOURS,
                    List.of(), Map.of());
        }
        List<Long> forcedCategoryIds = mapper.selectForcedCategoryIds(policy.getId());
        Map<Long, BigDecimal> overrides = mapper.selectOverrides(policy.getId()).stream()
                .collect(Collectors.toMap(InventoryIssuePolicyOverrideVO::getComponentId,
                        InventoryIssuePolicyOverrideVO::getDirectQuantityLimit));
        return new Policy(policy.getPolicyVersion(), policy.getDirectAmountThreshold(),
                policy.getValuableUnitCostThreshold(), policy.getDefaultDirectQuantityLimit(),
                policy.getLowStockRatio(), policy.getReservationTtlHours(),
                forcedCategoryIds, overrides);
    }

    private List<InventoryIssueBatchRecord> sortedBatches(List<InventoryIssueBatchRecord> batches) {
        return batches.stream()
                .sorted(Comparator
                        .comparing((InventoryIssueBatchRecord batch) -> batch.getExpiryDate() == null)
                        .thenComparing(InventoryIssueBatchRecord::getExpiryDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(InventoryIssueBatchRecord::getCreateTime)
                        .thenComparing(InventoryIssueBatchRecord::getId))
                .toList();
    }

    private InventoryIssueRequestRecord lockedVisible(Long id) {
        InventoryIssueRequestRecord request = mapper.selectRequestForUpdate(id);
        if (request == null || !access.canView(request)) {
            throw InventoryIssueApiException.requestNotFound();
        }
        return request;
    }

    private BigDecimal requestedFor(List<InventoryIssueLineRecord> lines, Long componentId) {
        return lines.stream().filter(line -> line.getComponentId().equals(componentId))
                .map(InventoryIssueLineRecord::getRequestedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal estimatedAmount(List<LineReservation> lineReservations) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineReservation lineReservation : lineReservations) {
            for (Map<String, Object> allocation : lineReservation.allocations()) {
                BigDecimal quantity = (BigDecimal) allocation.get("quantity");
                BigDecimal unitCost = (BigDecimal) allocation.get("unitCostSnapshot");
                total = total.add(quantity.multiply(unitCost));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void requireVersion(InventoryIssueRequestRecord issue, Integer version) {
        if (version == null) {
            throw InventoryIssueApiException.invalid();
        }
        if (!issue.getVersion().equals(version)) {
            throw InventoryIssueApiException.versionConflict(issue.getVersion());
        }
    }

    private void requireStatus(InventoryIssueRequestRecord issue, String status) {
        if (!status.equals(issue.getStatus())) {
            throw InventoryIssueApiException.invalidTransition();
        }
    }

    private Integer latestVersion(Long id) {
        InventoryIssueRequestRecord latest = mapper.selectRequest(id);
        return latest == null ? 0 : latest.getVersion();
    }

    private String operation(Long id, String action) {
        return "POST:/api/inventory-issue-requests/" + id + "/" + action;
    }

    private String reservationNo(LocalDate date) {
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000);
        return "RSV-" + day + "-" + String.format(Locale.ROOT, "%06d", suffix);
    }

    private List<String> parseReasons(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String optionalText(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw InventoryIssueApiException.invalid();
        }
        return normalized;
    }

    private String requiredText(String value, int max) {
        String normalized = optionalText(value, max);
        if (!StringUtils.hasText(normalized)) {
            throw InventoryIssueApiException.invalid();
        }
        return normalized;
    }

    private record LineReservation(InventoryIssueLineRecord line, List<Map<String, Object>> allocations) {
    }

    private record Policy(int version, BigDecimal amountThreshold, BigDecimal valuableUnitCost,
                          BigDecimal quantityLimit, BigDecimal lowStockRatio, int ttlHours,
                          List<Long> forcedCategoryIds, Map<Long, BigDecimal> overrides) {
        BigDecimal quantityLimit(Long componentId) {
            return overrides.getOrDefault(componentId, quantityLimit);
        }
    }
}
