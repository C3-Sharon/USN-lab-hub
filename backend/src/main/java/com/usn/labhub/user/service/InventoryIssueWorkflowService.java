package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.dto.inventory.InventoryIssueCommandDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueConfirmIssueDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueReallocateDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryReturnCommandDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryReturnCreateDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryWriteOffDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueApprovalRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueBatchRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueComponentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueFulfillmentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueFulfillmentLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssuePolicyRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationAllocationRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueReservationRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryReturnLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryReturnLineSummaryRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryReturnRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryWriteOffCommandRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryBalanceRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryMovementRecord;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePolicyOverrideVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.inventory.InventoryIssueAccessPolicy;
import com.usn.labhub.user.inventory.InventoryIssueApiException;
import com.usn.labhub.user.inventory.InventoryIssueSupport;
import com.usn.labhub.user.mapper.InventoryIssueMapper;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.mapper.SysUserMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final SysUserMapper userMapper;
    private final Environment environment;

    public InventoryIssueWorkflowService(InventoryIssueMapper mapper,
                                         ProcurementMapper procurementMapper,
                                         InventoryIssueAccessPolicy access,
                                         InventoryIssueSupport support,
                                         InventoryIssueRequestService requestService,
                                         ObjectMapper objectMapper,
                                         InventoryIssueReleaseSupport releaseSupport,
                                         InventoryIssueExpiryService expiryService,
                                         SysUserMapper userMapper,
                                         Environment environment) {
        this.mapper = mapper;
        this.procurementMapper = procurementMapper;
        this.access = access;
        this.support = support;
        this.requestService = requestService;
        this.objectMapper = objectMapper;
        this.releaseSupport = releaseSupport;
        this.expiryService = expiryService;
        this.userMapper = userMapper;
        this.environment = environment;
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

    @Transactional
    public Map<String, Object> reallocate(Long id, String key, InventoryIssueReallocateDTO request) {
        expiryService.expireIfNeeded(id);
        return support.idempotent(operation(id, "reallocate"), key, request,
                () -> reallocateValue(id, request));
    }

    @Transactional
    public Map<String, Object> confirmIssue(Long id, String key, String testFaultPoint,
                                            InventoryIssueConfirmIssueDTO request) {
        expiryService.expireIfNeeded(id);
        return support.idempotent(operation(id, "confirm-issue"), key, request,
                () -> confirmIssueValue(id, request, testFaultPoint));
    }

    @Transactional
    public Map<String, Object> initiateReturn(Long fulfillmentId, String key,
                                              InventoryReturnCreateDTO request) {
        return support.idempotent("POST:/api/inventory-fulfillments/" + fulfillmentId + "/returns",
                key, request, () -> initiateReturnValue(fulfillmentId, request));
    }

    public List<Map<String, Object>> listReturns(Long fulfillmentId) {
        return listReturnsValue(fulfillmentId);
    }

    @Transactional
    public Map<String, Object> confirmReturn(Long returnId, String key,
                                             InventoryReturnCommandDTO request) {
        return support.idempotent("POST:/api/inventory-returns/" + returnId + "/confirm",
                key, request, () -> confirmReturnValue(returnId, request));
    }

    @Transactional
    public Map<String, Object> rejectReturn(Long returnId, String key,
                                            InventoryReturnCommandDTO request) {
        return support.idempotent("POST:/api/inventory-returns/" + returnId + "/reject",
                key, request, () -> rejectReturnValue(returnId, request));
    }

    @Transactional
    public Map<String, Object> writeOff(Long batchId, String key, String type,
                                        InventoryWriteOffDTO request) {
        String action = "LOSS".equals(type) ? "losses" : "scraps";
        return support.idempotent("POST:/api/inventory/batches/" + batchId + "/" + action,
                key, request, () -> writeOffValue(batchId, type, request));
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

    private Map<String, Object> reallocateValue(Long id, InventoryIssueReallocateDTO request) {
        if (request == null || request.allocations() == null || request.allocations().isEmpty()) {
            throw InventoryIssueApiException.invalid();
        }
        InventoryIssueRequestRecord issue = lockedVisible(id);
        access.requireReallocate();
        if ("EXPIRED".equals(issue.getStatus())) {
            throw InventoryIssueApiException.reservationExpired();
        }
        requireVersion(issue, request.requestVersion());
        requireStatus(issue, "APPROVED");
        String reason = requiredText(request.reason(), 500);

        List<InventoryIssueLineRecord> lines = mapper.selectLinesForUpdate(id);
        List<Long> componentIds = lines.stream().map(InventoryIssueLineRecord::getComponentId)
                .distinct().sorted().toList();
        if (!componentIds.isEmpty()) {
            procurementMapper.selectInventoryBalancesForUpdate(componentIds);
        }

        List<InventoryIssueReservationRecord> reservations = mapper.selectReservationsForUpdate(id);
        Map<Long, InventoryIssueReservationRecord> reservationById = reservations.stream()
                .collect(Collectors.toMap(InventoryIssueReservationRecord::getId, Function.identity()));
        List<InventoryIssueReservationAllocationRecord> oldAllocations =
                mapper.selectAllocationsForRequest(id);
        List<InventoryIssueReservationRecord> activeReservations = reservations.stream()
                .filter(reservation -> "ACTIVE".equals(reservation.getStatus())).toList();
        if (activeReservations.isEmpty()) {
            throw InventoryIssueApiException.reservationNotActive();
        }

        Map<Long, BigDecimal> newByReservation = new HashMap<>();
        Map<Long, BigDecimal> newByBatch = new HashMap<>();
        for (InventoryIssueReallocateDTO.Allocation allocation : request.allocations()) {
            if (allocation == null || allocation.reservationId() == null
                    || allocation.batchId() == null || allocation.quantity() == null
                    || allocation.quantity().signum() <= 0
                    || Math.max(0, allocation.quantity().stripTrailingZeros().scale()) > 3) {
                throw InventoryIssueApiException.invalid();
            }
            InventoryIssueReservationRecord reservation = reservationById.get(allocation.reservationId());
            if (reservation == null || !"ACTIVE".equals(reservation.getStatus())
                    || !reservation.getRequestId().equals(id)) {
                throw InventoryIssueApiException.reservationNotActive();
            }
            newByReservation.merge(allocation.reservationId(), allocation.quantity(), BigDecimal::add);
            newByBatch.merge(allocation.batchId(), allocation.quantity(), BigDecimal::add);
        }
        for (InventoryIssueReservationRecord reservation : activeReservations) {
            BigDecimal planned = newByReservation.get(reservation.getId());
            if (planned == null || planned.compareTo(reservation.getQuantity()) != 0) {
                throw InventoryIssueApiException.batchAllocationConflict();
            }
        }

        Map<Long, BigDecimal> oldByBatch = new HashMap<>();
        for (InventoryIssueReservationAllocationRecord allocation : oldAllocations) {
            oldByBatch.merge(allocation.getBatchId(), allocation.getQuantity(), BigDecimal::add);
        }
        LinkedHashSet<Long> batchIdSet = new LinkedHashSet<>(oldByBatch.keySet());
        batchIdSet.addAll(newByBatch.keySet());
        List<Long> batchIds = batchIdSet.stream().sorted().toList();
        Map<Long, InventoryIssueBatchRecord> batchById = mapper.selectBatchesForUpdateByIds(batchIds)
                .stream().collect(Collectors.toMap(InventoryIssueBatchRecord::getId, Function.identity()));

        for (InventoryIssueReallocateDTO.Allocation allocation : request.allocations()) {
            InventoryIssueReservationRecord reservation = reservationById.get(allocation.reservationId());
            InventoryIssueBatchRecord batch = batchById.get(allocation.batchId());
            if (batch == null || !"AVAILABLE".equals(batch.getStatus())
                    || !batch.getComponentId().equals(reservation.getComponentId())
                    || (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(LocalDate.now()))) {
                throw InventoryIssueApiException.batchAllocationConflict();
            }
        }

        for (Long batchId : batchIdSet) {
            BigDecimal oldQuantity = oldByBatch.getOrDefault(batchId, BigDecimal.ZERO);
            BigDecimal net = newByBatch.getOrDefault(batchId, BigDecimal.ZERO)
                    .subtract(oldQuantity);
            if (net.signum() > 0) {
                if (mapper.reserveBatch(batchId, net) != 1) {
                    throw InventoryIssueApiException.batchAllocationConflict();
                }
            } else if (net.signum() < 0) {
                if (mapper.releaseBatch(batchId, net.negate()) != 1) {
                    throw InventoryIssueApiException.concurrentConflict();
                }
            }
        }

        List<Long> activeReservationIds = activeReservations.stream()
                .map(InventoryIssueReservationRecord::getId).toList();
        if (!activeReservationIds.isEmpty()) {
            mapper.deleteAllocationsByReservationIds(activeReservationIds);
        }
        for (InventoryIssueReallocateDTO.Allocation allocation : request.allocations()) {
            InventoryIssueBatchRecord batch = batchById.get(allocation.batchId());
            InventoryIssueReservationAllocationRecord record =
                    new InventoryIssueReservationAllocationRecord();
            record.setReservationId(allocation.reservationId());
            record.setBatchId(allocation.batchId());
            record.setQuantity(allocation.quantity());
            record.setUnitCostSnapshot(batch.getUnitCost());
            mapper.insertReservationAllocation(record);
        }

        support.audit(UUID.randomUUID().toString(), "REALLOCATE", "INVENTORY_ISSUE_REQUEST", id,
                "APPROVED", "APPROVED", reason, Map.of("reservationIds", activeReservationIds));
        Map<String, Object> result = support.asMap(requestService.detail(id));
        result.put("reservationVersion", activeReservations.get(0).getVersion());
        return result;
    }

    private Map<String, Object> confirmIssueValue(Long id, InventoryIssueConfirmIssueDTO request,
                                                  String testFaultPoint) {
        InventoryIssueRequestRecord issue = lockedVisible(id);
        access.requireConfirmIssue(issue);
        if ("ISSUED".equals(issue.getStatus())) {
            throw InventoryIssueApiException.issueAlreadyConfirmed();
        }
        if ("EXPIRED".equals(issue.getStatus())) {
            throw InventoryIssueApiException.reservationExpired();
        }
        requireVersion(issue, request == null ? null : request.requestVersion());
        requireStatus(issue, "APPROVED");
        Integer reservationVersion = request == null ? null : request.reservationVersion();
        if (reservationVersion == null) {
            throw InventoryIssueApiException.invalid();
        }
        String note = optionalText(request == null ? null : request.note(), 500);
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();

        List<InventoryIssueLineRecord> lines = mapper.selectLinesForUpdate(id);
        if (lines.isEmpty()) {
            throw InventoryIssueApiException.linesRequired();
        }
        List<Long> componentIds = lines.stream().map(InventoryIssueLineRecord::getComponentId)
                .distinct().sorted().toList();
        Map<Long, InventoryBalanceRecord> balanceByComponent =
                procurementMapper.selectInventoryBalancesForUpdate(componentIds).stream()
                        .collect(Collectors.toMap(InventoryBalanceRecord::getComponentId,
                                Function.identity()));
        Map<Long, String> unitByComponent = lines.stream().collect(Collectors.toMap(
                InventoryIssueLineRecord::getComponentId, InventoryIssueLineRecord::getUnit,
                (left, right) -> left));

        List<InventoryIssueReservationRecord> reservations = mapper.selectReservationsForUpdate(id);
        List<InventoryIssueReservationAllocationRecord> allocations =
                mapper.selectAllocationsForRequest(id);
        if (reservations.isEmpty() || allocations.isEmpty()) {
            throw InventoryIssueApiException.reservationNotActive();
        }
        Map<Long, InventoryIssueReservationRecord> reservationById = reservations.stream()
                .collect(Collectors.toMap(InventoryIssueReservationRecord::getId, Function.identity()));
        for (InventoryIssueReservationRecord reservation : reservations) {
            if (!"ACTIVE".equals(reservation.getStatus())) {
                throw InventoryIssueApiException.reservationNotActive();
            }
            if (reservation.getExpiresAt() != null && reservation.getExpiresAt().isBefore(dbNow)) {
                throw InventoryIssueApiException.reservationExpired();
            }
            if (!reservationVersion.equals(reservation.getVersion())) {
                throw InventoryIssueApiException.versionConflict(reservation.getVersion());
            }
        }
        List<Long> batchIds = allocations.stream()
                .map(InventoryIssueReservationAllocationRecord::getBatchId)
                .distinct().sorted().toList();
        mapper.selectBatchesForUpdateByIds(batchIds);

        Map<Long, BigDecimal> allocatedByReservation = new HashMap<>();
        Map<Long, BigDecimal> deltaByComponent = new HashMap<>();
        for (InventoryIssueReservationAllocationRecord allocation : allocations) {
            InventoryIssueReservationRecord reservation =
                    reservationById.get(allocation.getReservationId());
            if (reservation == null || !"ACTIVE".equals(reservation.getStatus())) {
                throw InventoryIssueApiException.reservationNotActive();
            }
            allocatedByReservation.merge(allocation.getReservationId(), allocation.getQuantity(),
                    BigDecimal::add);
            deltaByComponent.merge(reservation.getComponentId(), allocation.getQuantity(),
                    BigDecimal::add);
        }
        for (InventoryIssueReservationRecord reservation : reservations) {
            if (allocatedByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
                    .compareTo(reservation.getQuantity()) != 0) {
                throw InventoryIssueApiException.concurrentConflict();
            }
        }

        SysUser actor = userMapper.selectById(access.userId());
        if (actor == null) {
            throw InventoryIssueApiException.operationDenied();
        }

        for (Map.Entry<Long, BigDecimal> entry : deltaByComponent.entrySet()) {
            if (mapper.consumeBalance(entry.getKey(), entry.getValue(), dbNow) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
        }

        InventoryIssueFulfillmentRecord fulfillment = new InventoryIssueFulfillmentRecord();
        fulfillment.setFulfillmentNo(number("FUL", dbNow.toLocalDate()));
        fulfillment.setRequestId(id);
        fulfillment.setOperatorId(actor.getId());
        fulfillment.setOperatorName(actor.getUsername());
        fulfillment.setRecipientId(issue.getRequesterId());
        fulfillment.setRecipientName(issue.getRequesterName());
        fulfillment.setIssuedAt(dbNow);
        fulfillment.setNote(note);
        mapper.insertFulfillment(fulfillment);

        List<InventoryIssueReservationAllocationRecord> ordered = allocations.stream()
                .sorted(Comparator.comparing(InventoryIssueReservationAllocationRecord::getReservationId)
                        .thenComparing(InventoryIssueReservationAllocationRecord::getId))
                .toList();
        Map<Long, BigDecimal> consumedByComponent = new HashMap<>();
        List<Map<String, Object>> movementResults = new ArrayList<>();
        boolean firstMovement = true;
        for (InventoryIssueReservationAllocationRecord allocation : ordered) {
            InventoryIssueReservationRecord reservation =
                    reservationById.get(allocation.getReservationId());
            Long componentId = reservation.getComponentId();
            BigDecimal starting = balanceByComponent.get(componentId).getOnHandQuantity();
            BigDecimal before = starting.subtract(consumedByComponent.getOrDefault(componentId,
                    BigDecimal.ZERO));
            BigDecimal after = before.subtract(allocation.getQuantity());

            if (mapper.consumeBatch(allocation.getBatchId(), allocation.getQuantity()) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }

            InventoryMovementRecord movement = new InventoryMovementRecord();
            movement.setMovementNo(number("MOV", dbNow.toLocalDate()));
            movement.setMovementType("ISSUE_OUT");
            movement.setComponentId(componentId);
            movement.setBatchId(allocation.getBatchId());
            movement.setSourceType("FULFILLMENT_LINE");
            movement.setSourceId(-1L);
            movement.setQuantityDelta(allocation.getQuantity().negate());
            movement.setBalanceBefore(before);
            movement.setBalanceAfter(after);
            movement.setUnit(unitByComponent.get(componentId));
            movement.setUnitCost(allocation.getUnitCostSnapshot());
            movement.setOperatorId(actor.getId());
            movement.setOccurredAt(dbNow);
            movement.setRequestId(issue.getRequestNo());
            procurementMapper.insertInventoryMovementTyped(movement);

            InventoryIssueFulfillmentLineRecord fulfillmentLine =
                    new InventoryIssueFulfillmentLineRecord();
            fulfillmentLine.setFulfillmentId(fulfillment.getId());
            fulfillmentLine.setRequestLineId(reservation.getRequestLineId());
            fulfillmentLine.setReservationAllocationId(allocation.getId());
            fulfillmentLine.setBatchId(allocation.getBatchId());
            fulfillmentLine.setQuantity(allocation.getQuantity());
            fulfillmentLine.setUnitCostSnapshot(allocation.getUnitCostSnapshot());
            fulfillmentLine.setMovementId(movement.getId());
            mapper.insertFulfillmentLine(fulfillmentLine);

            procurementMapper.updateInventoryMovementSourceId(movement.getId(),
                    fulfillmentLine.getId());

            consumedByComponent.merge(componentId, allocation.getQuantity(), BigDecimal::add);
            Map<String, Object> movementResult = new LinkedHashMap<>();
            movementResult.put("movementId", movement.getId());
            movementResult.put("movementType", "ISSUE_OUT");
            movementResult.put("batchId", allocation.getBatchId());
            movementResult.put("quantityDelta", movement.getQuantityDelta());
            movementResult.put("balanceBefore", before);
            movementResult.put("balanceAfter", after);
            movementResults.add(movementResult);
            if (firstMovement) {
                firstMovement = false;
                failIfConfigured("AFTER_FIRST_MOVEMENT", testFaultPoint);
            }
        }

        for (InventoryIssueReservationRecord reservation : reservations) {
            if (mapper.consumeReservation(reservation.getId()) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
        }
        for (InventoryIssueLineRecord line : lines) {
            if (mapper.consumeLine(line.getId(), line.getRequestedQuantity()) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }
        }
        if (mapper.issueRequest(id, issue.getVersion(), dbNow, actor.getId(), dbNow) != 1) {
            throw InventoryIssueApiException.versionConflict(latestVersion(id));
        }
        support.audit(UUID.randomUUID().toString(), "CONFIRM_ISSUE", "INVENTORY_ISSUE_REQUEST", id,
                "APPROVED", "ISSUED", note, Map.of("fulfillmentId", fulfillment.getId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", id);
        result.put("status", "ISSUED");
        result.put("fulfillmentId", fulfillment.getId());
        result.put("fulfillmentNo", fulfillment.getFulfillmentNo());
        result.put("requestVersion", issue.getVersion() + 1);
        result.put("movements", movementResults);
        return result;
    }

    private Map<String, Object> initiateReturnValue(Long fulfillmentId,
                                                    InventoryReturnCreateDTO request) {
        InventoryIssueFulfillmentRecord fulfillment = mapper.selectFulfillment(fulfillmentId);
        if (fulfillment == null) {
            throw InventoryIssueApiException.fulfillmentNotFound();
        }
        InventoryIssueRequestRecord issue = mapper.selectRequest(fulfillment.getRequestId());
        if (issue == null || !access.canView(issue)) {
            throw InventoryIssueApiException.requestNotFound();
        }
        access.requireInitiateReturn(issue);
        if (!"ISSUED".equals(issue.getStatus())) {
            throw InventoryIssueApiException.invalidTransition();
        }
        mapper.selectRequestForUpdate(issue.getId());

        List<InventoryIssueFulfillmentLineRecord> fulfillmentLines =
                mapper.selectFulfillmentLines(fulfillmentId);
        if (fulfillmentLines.isEmpty()) {
            throw InventoryIssueApiException.fulfillmentNotFound();
        }
        Map<Long, InventoryIssueFulfillmentLineRecord> lineById = fulfillmentLines.stream()
                .collect(Collectors.toMap(InventoryIssueFulfillmentLineRecord::getId,
                        Function.identity()));

        Map<Long, BigDecimal> requestedByLine = new LinkedHashMap<>();
        for (InventoryReturnCreateDTO.Line line : request.lines()) {
            if (line == null || line.fulfillmentLineId() == null || line.quantity() == null
                    || line.quantity().signum() <= 0
                    || Math.max(0, line.quantity().stripTrailingZeros().scale()) > 3
                    || !lineById.containsKey(line.fulfillmentLineId())
                    || requestedByLine.containsKey(line.fulfillmentLineId())) {
                throw InventoryIssueApiException.returnQuantityInvalid();
            }
            requestedByLine.put(line.fulfillmentLineId(), line.quantity());
        }

        Map<Long, BigDecimal> alreadyReturned = new HashMap<>();
        for (InventoryReturnLineSummaryRecord summary
                : mapper.selectReturnLineSummaries(fulfillmentId)) {
            if (Set.of("PENDING_CONFIRMATION", "CONFIRMED").contains(summary.getStatus())) {
                alreadyReturned.merge(summary.getFulfillmentLineId(), summary.getQuantity(),
                        BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : requestedByLine.entrySet()) {
            InventoryIssueFulfillmentLineRecord line = lineById.get(entry.getKey());
            BigDecimal already = alreadyReturned.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (already.add(entry.getValue()).compareTo(line.getQuantity()) > 0) {
                throw InventoryIssueApiException.returnExceedsIssued();
            }
        }

        String reason = requiredText(request.reason(), 500);
        if (reason.length() < 2) {
            throw InventoryIssueApiException.invalid();
        }
        Long actorId = access.userId();
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();

        InventoryReturnRecord record = new InventoryReturnRecord();
        record.setReturnNo(number("RTN", dbNow.toLocalDate()));
        record.setFulfillmentId(fulfillmentId);
        record.setRequestId(issue.getId());
        record.setStatus("PENDING_CONFIRMATION");
        record.setReason(reason);
        record.setRequestedBy(actorId);
        record.setVersion(1);
        record.setCreatedBy(actorId);
        record.setCreateTime(dbNow);
        record.setUpdatedBy(actorId);
        record.setUpdateTime(dbNow);
        mapper.insertReturn(record);

        for (Map.Entry<Long, BigDecimal> entry : requestedByLine.entrySet()) {
            InventoryIssueFulfillmentLineRecord line = lineById.get(entry.getKey());
            InventoryReturnLineRecord returnLine = new InventoryReturnLineRecord();
            returnLine.setReturnId(record.getId());
            returnLine.setFulfillmentLineId(entry.getKey());
            returnLine.setBatchId(line.getBatchId());
            returnLine.setQuantity(entry.getValue());
            mapper.insertReturnLine(returnLine);
        }

        support.audit(UUID.randomUUID().toString(), "INITIATE_RETURN", "INVENTORY_RETURN",
                record.getId(), "PENDING_CONFIRMATION", "PENDING_CONFIRMATION", reason,
                Map.of("fulfillmentId", fulfillmentId, "requestId", issue.getId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("returnId", record.getId());
        result.put("returnNo", record.getReturnNo());
        result.put("status", "PENDING_CONFIRMATION");
        result.put("version", 1);
        return result;
    }

    private List<Map<String, Object>> listReturnsValue(Long fulfillmentId) {
        InventoryIssueFulfillmentRecord fulfillment = mapper.selectFulfillment(fulfillmentId);
        if (fulfillment == null) {
            throw InventoryIssueApiException.fulfillmentNotFound();
        }
        InventoryIssueRequestRecord issue = mapper.selectRequest(fulfillment.getRequestId());
        if (issue == null || !access.canView(issue)) {
            throw InventoryIssueApiException.requestNotFound();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (InventoryReturnRecord record : mapper.selectReturnsByFulfillment(fulfillmentId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", record.getId());
            item.put("returnNo", record.getReturnNo());
            item.put("status", record.getStatus());
            item.put("reason", record.getReason());
            item.put("requestedBy", record.getRequestedBy());
            item.put("confirmedBy", record.getConfirmedBy());
            item.put("version", record.getVersion());
            item.put("lines", mapper.selectReturnLines(record.getId()).stream()
                    .map(line -> Map.of("fulfillmentLineId", line.getFulfillmentLineId(),
                            "batchId", line.getBatchId(), "quantity", line.getQuantity()))
                    .toList());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> confirmReturnValue(Long returnId, InventoryReturnCommandDTO request) {
        InventoryReturnRecord pre = mapper.selectReturn(returnId);
        if (pre == null) {
            throw InventoryIssueApiException.returnNotFound();
        }
        access.requireReturnDecision();

        InventoryIssueRequestRecord issue = mapper.selectRequestForUpdate(pre.getRequestId());
        if (issue == null || !access.canView(issue)) {
            throw InventoryIssueApiException.requestNotFound();
        }
        if (!"ISSUED".equals(issue.getStatus())) {
            throw InventoryIssueApiException.invalidTransition();
        }
        mapper.selectLinesForUpdate(issue.getId());

        List<InventoryReturnLineRecord> preLines = mapper.selectReturnLines(returnId);
        if (preLines.isEmpty()) {
            throw InventoryIssueApiException.returnQuantityInvalid();
        }
        List<Long> batchIds = preLines.stream().map(InventoryReturnLineRecord::getBatchId)
                .distinct().sorted().toList();
        List<Long> componentIds = mapper.selectBatchesByIds(batchIds).stream()
                .map(InventoryIssueBatchRecord::getComponentId).distinct().sorted().toList();
        if (componentIds.isEmpty()) {
            throw InventoryIssueApiException.batchNotFound();
        }

        List<InventoryBalanceRecord> balances =
                procurementMapper.selectInventoryBalancesForUpdate(componentIds);
        Map<Long, InventoryBalanceRecord> balanceByComponent = balances.stream()
                .collect(Collectors.toMap(InventoryBalanceRecord::getComponentId, Function.identity()));
        Map<Long, InventoryIssueBatchRecord> batchById =
                mapper.selectBatchesForUpdateByIds(batchIds).stream()
                        .collect(Collectors.toMap(InventoryIssueBatchRecord::getId, Function.identity()));
        mapper.selectReservationsForUpdate(issue.getId());
        mapper.selectAllocationsForRequest(issue.getId());
        mapper.selectFulfillmentForUpdate(pre.getFulfillmentId());
        Map<Long, InventoryIssueFulfillmentLineRecord> fulfillmentLineById =
                mapper.selectFulfillmentLinesForUpdate(pre.getFulfillmentId()).stream()
                        .collect(Collectors.toMap(InventoryIssueFulfillmentLineRecord::getId,
                                Function.identity()));

        InventoryReturnRecord lockedReturn = mapper.selectReturnForUpdate(returnId);
        if (lockedReturn == null) {
            throw InventoryIssueApiException.returnNotFound();
        }
        if (!"PENDING_CONFIRMATION".equals(lockedReturn.getStatus())) {
            throw InventoryIssueApiException.invalidTransition();
        }
        if (!lockedReturn.getVersion().equals(request.returnVersion())) {
            throw InventoryIssueApiException.versionConflict(lockedReturn.getVersion());
        }
        List<InventoryReturnLineRecord> returnLines = mapper.selectReturnLinesForUpdate(returnId);
        if (returnLines.isEmpty()) {
            throw InventoryIssueApiException.returnQuantityInvalid();
        }

        Map<Long, BigDecimal> confirmedByLine = new HashMap<>();
        for (InventoryReturnLineSummaryRecord summary
                : mapper.selectReturnLineSummaries(pre.getFulfillmentId())) {
            if ("CONFIRMED".equals(summary.getStatus())) {
                confirmedByLine.merge(summary.getFulfillmentLineId(), summary.getQuantity(),
                        BigDecimal::add);
            }
        }
        for (InventoryReturnLineRecord line : returnLines) {
            InventoryIssueFulfillmentLineRecord fulfillmentLine =
                    fulfillmentLineById.get(line.getFulfillmentLineId());
            if (fulfillmentLine == null) {
                throw InventoryIssueApiException.returnQuantityInvalid();
            }
            BigDecimal already = confirmedByLine.getOrDefault(line.getFulfillmentLineId(),
                    BigDecimal.ZERO);
            if (already.add(line.getQuantity()).compareTo(fulfillmentLine.getQuantity()) > 0) {
                throw InventoryIssueApiException.returnExceedsIssued();
            }
        }

        LocalDateTime dbNow = mapper.selectCurrentTimestamp();
        Map<Long, BigDecimal> runningOnHand = new HashMap<>();
        for (InventoryBalanceRecord balance : balances) {
            runningOnHand.put(balance.getComponentId(), balance.getOnHandQuantity());
        }
        List<Map<String, Object>> movements = new ArrayList<>();
        for (InventoryReturnLineRecord line : returnLines) {
            InventoryIssueFulfillmentLineRecord fulfillmentLine =
                    fulfillmentLineById.get(line.getFulfillmentLineId());
            InventoryIssueBatchRecord batch = batchById.get(line.getBatchId());
            InventoryBalanceRecord balance = balanceByComponent.get(batch.getComponentId());
            if (batch == null || balance == null) {
                throw InventoryIssueApiException.concurrentConflict();
            }
            if (mapper.increaseBatchCurrent(batch.getId(), line.getQuantity()) != 1
                    || mapper.increaseOnHand(batch.getComponentId(), line.getQuantity(), dbNow) != 1) {
                throw InventoryIssueApiException.concurrentConflict();
            }

            BigDecimal before = runningOnHand.get(batch.getComponentId());
            BigDecimal after = before.add(line.getQuantity());
            runningOnHand.put(batch.getComponentId(), after);

            InventoryMovementRecord movement = new InventoryMovementRecord();
            movement.setMovementNo(number("MOV", dbNow.toLocalDate()));
            movement.setMovementType("RETURN_IN");
            movement.setComponentId(batch.getComponentId());
            movement.setBatchId(batch.getId());
            movement.setSourceType("RETURN_LINE");
            movement.setSourceId(line.getId());
            movement.setQuantityDelta(line.getQuantity());
            movement.setBalanceBefore(before);
            movement.setBalanceAfter(after);
            movement.setUnit(balance.getUnit());
            movement.setUnitCost(fulfillmentLine.getUnitCostSnapshot());
            movement.setOperatorId(access.userId());
            movement.setOccurredAt(dbNow);
            movement.setRequestId(issue.getRequestNo());
            procurementMapper.insertInventoryMovementTyped(movement);

            Map<String, Object> movementResult = new LinkedHashMap<>();
            movementResult.put("movementId", movement.getId());
            movementResult.put("movementType", "RETURN_IN");
            movementResult.put("batchId", batch.getId());
            movementResult.put("quantityDelta", movement.getQuantityDelta());
            movementResult.put("balanceBefore", before);
            movementResult.put("balanceAfter", after);
            movements.add(movementResult);
        }

        if (mapper.confirmReturn(returnId, lockedReturn.getVersion(), access.userId(), dbNow) != 1) {
            throw InventoryIssueApiException.versionConflict(latestReturnVersion(returnId));
        }
        support.audit(UUID.randomUUID().toString(), "CONFIRM_RETURN", "INVENTORY_RETURN",
                returnId, "PENDING_CONFIRMATION", "CONFIRMED", null,
                Map.of("fulfillmentId", pre.getFulfillmentId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("returnId", returnId);
        result.put("status", "CONFIRMED");
        result.put("returnVersion", lockedReturn.getVersion() + 1);
        result.put("movements", movements);
        return result;
    }

    private Map<String, Object> rejectReturnValue(Long returnId, InventoryReturnCommandDTO request) {
        access.requireReturnDecision();
        InventoryReturnRecord record = mapper.selectReturnForUpdate(returnId);
        if (record == null) {
            throw InventoryIssueApiException.returnNotFound();
        }
        if (!"PENDING_CONFIRMATION".equals(record.getStatus())) {
            throw InventoryIssueApiException.invalidTransition();
        }
        if (!record.getVersion().equals(request.returnVersion())) {
            throw InventoryIssueApiException.versionConflict(record.getVersion());
        }
        String comment = optionalText(request.comment(), 500);
        LocalDateTime dbNow = mapper.selectCurrentTimestamp();
        if (mapper.rejectReturn(returnId, record.getVersion(), access.userId(), dbNow) != 1) {
            throw InventoryIssueApiException.versionConflict(latestReturnVersion(returnId));
        }
        support.audit(UUID.randomUUID().toString(), "REJECT_RETURN", "INVENTORY_RETURN",
                returnId, "PENDING_CONFIRMATION", "REJECTED", comment, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("returnId", returnId);
        result.put("status", "REJECTED");
        result.put("returnVersion", record.getVersion() + 1);
        return result;
    }

    private Map<String, Object> writeOffValue(Long batchId, String type, InventoryWriteOffDTO request) {
        access.requireWriteOff();
        if (request.quantity() == null || request.quantity().signum() <= 0
                || Math.max(0, request.quantity().stripTrailingZeros().scale()) > 3) {
            throw InventoryIssueApiException.invalid();
        }
        if (!StringUtils.hasText(request.reason())) {
            throw InventoryIssueApiException.writeOffReasonRequired();
        }
        String reason = request.reason().trim();
        if (reason.length() > 500) {
            throw InventoryIssueApiException.invalid();
        }

        InventoryIssueBatchRecord batch = mapper.selectBatch(batchId);
        if (batch == null) {
            throw InventoryIssueApiException.batchNotFound();
        }
        Long componentId = batch.getComponentId();
        List<InventoryBalanceRecord> balances =
                procurementMapper.selectInventoryBalancesForUpdate(List.of(componentId));
        if (balances.isEmpty()) {
            throw InventoryIssueApiException.reservedStockProtected();
        }
        InventoryBalanceRecord balance = balances.get(0);
        List<InventoryIssueBatchRecord> locked = mapper.selectBatchesForUpdateByIds(List.of(batchId));
        if (locked.isEmpty()) {
            throw InventoryIssueApiException.batchNotFound();
        }
        InventoryIssueBatchRecord lockedBatch = locked.get(0);

        LocalDateTime dbNow = mapper.selectCurrentTimestamp();
        if (mapper.writeOffBalance(componentId, request.quantity(), dbNow) != 1) {
            throw InventoryIssueApiException.reservedStockProtected();
        }
        if (mapper.writeOffBatch(batchId, request.quantity()) != 1) {
            throw InventoryIssueApiException.reservedStockProtected();
        }

        BigDecimal before = balance.getOnHandQuantity();
        BigDecimal after = before.subtract(request.quantity());
        InventoryMovementRecord movement = new InventoryMovementRecord();
        movement.setMovementNo(number("MOV", dbNow.toLocalDate()));
        movement.setMovementType("LOSS".equals(type) ? "LOSS_OUT" : "SCRAP_OUT");
        movement.setComponentId(componentId);
        movement.setBatchId(batchId);
        movement.setSourceType("WRITE_OFF_COMMAND");
        movement.setSourceId(-1L);
        movement.setQuantityDelta(request.quantity().negate());
        movement.setBalanceBefore(before);
        movement.setBalanceAfter(after);
        movement.setUnit(balance.getUnit());
        movement.setUnitCost(lockedBatch.getUnitCost());
        movement.setReason(reason);
        movement.setOperatorId(access.userId());
        movement.setOccurredAt(dbNow);
        movement.setRequestId(UUID.randomUUID().toString());
        procurementMapper.insertInventoryMovementTyped(movement);

        InventoryWriteOffCommandRecord command = new InventoryWriteOffCommandRecord();
        command.setCommandNo(number("WOF", dbNow.toLocalDate()));
        command.setType(type);
        command.setComponentId(componentId);
        command.setBatchId(batchId);
        command.setQuantity(request.quantity());
        command.setReason(reason);
        command.setOperatorId(access.userId());
        command.setOccurredAt(dbNow);
        command.setMovementId(movement.getId());
        command.setRequestId(movement.getRequestId());
        command.setCreatedBy(access.userId());
        command.setCreateTime(dbNow);
        mapper.insertWriteOffCommand(command);
        procurementMapper.updateInventoryMovementSourceId(movement.getId(), command.getId());

        support.audit(UUID.randomUUID().toString(), "WRITE_OFF_" + type,
                "INVENTORY_WRITE_OFF_COMMAND", command.getId(), null, type, reason,
                Map.of("batchId", batchId, "quantity", request.quantity()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commandId", command.getId());
        result.put("commandNo", command.getCommandNo());
        result.put("type", type);
        result.put("movementId", movement.getId());
        result.put("batchId", batchId);
        result.put("quantityDelta", movement.getQuantityDelta());
        result.put("balanceBefore", before);
        result.put("balanceAfter", after);
        return result;
    }

    private Integer latestReturnVersion(Long id) {
        InventoryReturnRecord latest = mapper.selectReturn(id);
        return latest == null ? 0 : latest.getVersion();
    }

    private void failIfConfigured(String point, String requestedPoint) {
        String configured = environment.getProperty("usnhub.inventory.issue.test-fault-point");
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")
                && (point.equals(configured) || point.equals(requestedPoint))) {
            throw InventoryIssueApiException.issueTransactionFailed();
        }
    }

    private String number(String prefix, LocalDate date) {
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000);
        return prefix + "-" + day + "-" + String.format(Locale.ROOT, "%06d", suffix);
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
