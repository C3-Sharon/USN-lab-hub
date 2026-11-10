package com.usn.labhub.user.service;

import com.usn.labhub.user.domain.dto.procurement.ProcurementReceiptCommandDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementReceiptCreateDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementReceiptLineDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementReceiptUpdateDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.entity.procurement.InventoryBalanceRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryBatchRecord;
import com.usn.labhub.user.domain.entity.procurement.InventoryMovementRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementOrderRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementReceiptLineRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementReceiptRecord;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderLineVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementReceiptVO;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.mapper.SysUserMapper;
import com.usn.labhub.user.procurement.ProcurementAccessPolicy;
import com.usn.labhub.user.procurement.ProcurementApiException;
import com.usn.labhub.user.procurement.ProcurementSupport;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProcurementReceiptService {
    private static final Set<String> RECEIVABLE_ORDER_STATUSES = Set.of("ORDERED", "PARTIALLY_RECEIVED");

    private final ProcurementMapper mapper;
    private final SysUserMapper userMapper;
    private final ProcurementAccessPolicy access;
    private final ProcurementSupport support;
    private final Environment environment;

    public ProcurementReceiptService(ProcurementMapper mapper, SysUserMapper userMapper,
                                     ProcurementAccessPolicy access, ProcurementSupport support,
                                     Environment environment) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.access = access;
        this.support = support;
        this.environment = environment;
    }

    @Transactional
    public Map<String, Object> create(Long orderId, String key, ProcurementReceiptCreateDTO request) {
        access.requireReceiptManagement();
        return support.idempotent("POST:/api/procurement-orders/" + orderId + "/receipts", key, request,
                () -> createValue(orderId, request));
    }

    public List<ProcurementReceiptVO> receipts(Long orderId) {
        visibleOrder(orderId);
        List<ProcurementReceiptVO> receipts = mapper.selectReceipts(orderId);
        receipts.forEach(receipt -> receipt.setLines(mapper.selectReceiptLineViews(receipt.getId())));
        return receipts;
    }

    public ProcurementReceiptVO receipt(Long receiptId) {
        ProcurementReceiptRecord receipt = visibleReceipt(receiptId);
        return receiptView(receipt.getId());
    }

    @Transactional
    public ProcurementReceiptVO update(Long receiptId, ProcurementReceiptUpdateDTO request) {
        access.requireReceiptManagement();
        if (request == null) {
            throw ProcurementApiException.invalid();
        }
        ProcurementReceiptRecord initial = requiredReceipt(receiptId);
        ProcurementOrderRecord order = lockedOrder(initial.getOrderId());
        ProcurementReceiptRecord receipt = requiredLockedReceipt(receiptId, order.getId());
        requireDraft(receipt);
        requireVersions(order, request.orderVersion(), receipt, request.receiptVersion());
        List<ProcurementReceiptLineRecord> lines = validatedLines(order.getId(), request.lines());
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateReceiptDraft(receiptId, receipt.getVersion(), receivedAt(request.receivedAt()),
                optionalText(request.note(), 1000), access.userId(), now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectReceipt(receiptId).getVersion());
        }
        mapper.deleteReceiptLines(receiptId);
        lines.forEach(line -> mapper.insertReceiptLine(receiptId, line));
        touchOrder(order, now);
        support.audit(UUID.randomUUID().toString(), "UPDATE_RECEIPT_DRAFT", "PROCUREMENT_RECEIPT",
                receiptId, "DRAFT", "DRAFT", null, Map.of("orderId", order.getId()));
        return receiptView(receiptId);
    }

    @Transactional
    public Map<String, Object> cancel(Long receiptId, String key, ProcurementReceiptCommandDTO request) {
        access.requireReceiptManagement();
        return support.idempotent("POST:/api/procurement-receipts/" + receiptId + "/cancel", key, request,
                () -> cancelValue(receiptId, request));
    }

    @Transactional
    public Map<String, Object> confirm(Long receiptId, String key, String testFaultPoint,
                                       ProcurementReceiptCommandDTO request) {
        access.requireReceiptManagement();
        return support.idempotent("POST:/api/procurement-receipts/" + receiptId + "/confirm", key, request,
                () -> confirmValue(receiptId, request, testFaultPoint));
    }

    private Map<String, Object> createValue(Long orderId, ProcurementReceiptCreateDTO request) {
        if (request == null) {
            throw ProcurementApiException.invalid();
        }
        ProcurementOrderRecord order = lockedOrder(orderId);
        requireReceivable(order);
        requireOrderVersion(order, request == null ? null : request.orderVersion());
        List<ProcurementReceiptLineRecord> lines = validatedLines(orderId, request.lines());
        LocalDateTime now = LocalDateTime.now();
        ProcurementReceiptRecord receipt = new ProcurementReceiptRecord();
        receipt.setReceiptNo(number("RCV"));
        receipt.setOrderId(orderId);
        receipt.setReceivedAt(receivedAt(request.receivedAt()));
        receipt.setNote(optionalText(request.note(), 1000));
        receipt.setCreatedBy(access.userId());
        receipt.setUpdatedBy(access.userId());
        receipt.setCreateTime(now);
        receipt.setUpdateTime(now);
        try {
            mapper.insertReceipt(receipt);
        } catch (DuplicateKeyException exception) {
            throw ProcurementApiException.receiptDraftExists();
        }
        lines.forEach(line -> mapper.insertReceiptLine(receipt.getId(), line));
        touchOrder(order, now);
        support.audit(UUID.randomUUID().toString(), "CREATE_RECEIPT_DRAFT", "PROCUREMENT_RECEIPT",
                receipt.getId(), null, "DRAFT", null, Map.of("orderId", orderId));
        return support.asMap(receiptView(receipt.getId()));
    }

    private Map<String, Object> cancelValue(Long receiptId, ProcurementReceiptCommandDTO request) {
        ProcurementReceiptRecord initial = requiredReceipt(receiptId);
        ProcurementOrderRecord order = lockedOrder(initial.getOrderId());
        ProcurementReceiptRecord receipt = requiredLockedReceipt(receiptId, order.getId());
        requireDraft(receipt);
        requireVersions(order, request == null ? null : request.orderVersion(),
                receipt, request == null ? null : request.receiptVersion());
        LocalDateTime now = LocalDateTime.now();
        if (mapper.cancelReceipt(receiptId, receipt.getVersion(), access.userId(), now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectReceipt(receiptId).getVersion());
        }
        touchOrder(order, now);
        support.audit(UUID.randomUUID().toString(), "CANCEL_RECEIPT", "PROCUREMENT_RECEIPT",
                receiptId, "DRAFT", "CANCELED", null, Map.of("orderId", order.getId()));
        return support.asMap(receiptView(receiptId));
    }

    private Map<String, Object> confirmValue(Long receiptId, ProcurementReceiptCommandDTO request,
                                             String testFaultPoint) {
        ProcurementReceiptRecord initial = requiredReceipt(receiptId);
        ProcurementOrderRecord order = lockedOrder(initial.getOrderId());

        List<ProcurementReceiptLineRecord> initialReceiptLines = mapper.selectReceiptLines(receiptId);
        if (initialReceiptLines.isEmpty()) {
            throw ProcurementApiException.receiptLinesRequired();
        }
        List<Long> orderLineIds = initialReceiptLines.stream().map(ProcurementReceiptLineRecord::getOrderLineId)
                .distinct().sorted().toList();
        List<ProcurementOrderLineVO> orderLines = mapper.selectOrderLinesForUpdate(order.getId(), orderLineIds);
        ProcurementReceiptRecord receipt = requiredLockedReceipt(receiptId, order.getId());
        if ("CONFIRMED".equals(receipt.getStatus())) {
            throw ProcurementApiException.receiptAlreadyConfirmed();
        }
        requireReceivable(order);
        requireOrderVersion(order, request == null ? null : request.orderVersion());
        requireDraft(receipt);
        if (request == null || request.receiptVersion() == null) {
            throw ProcurementApiException.invalid();
        }
        if (!receipt.getVersion().equals(request.receiptVersion())) {
            throw ProcurementApiException.versionConflict(receipt.getVersion());
        }
        List<ProcurementReceiptLineRecord> receiptLines = mapper.selectReceiptLinesForUpdate(receiptId);
        if (receiptLines.size() != initialReceiptLines.size() || orderLines.size() != orderLineIds.size()) {
            throw ProcurementApiException.invalidTransition();
        }

        Map<Long, ProcurementOrderLineVO> ordersById = orderLines.stream()
                .collect(Collectors.toMap(ProcurementOrderLineVO::getId, Function.identity()));
        validateConfirmLines(receiptLines, ordersById);
        List<Long> componentIds = receiptLines.stream().map(ProcurementReceiptLineRecord::getComponentId)
                .distinct().sorted().toList();
        Map<Long, String> units = receiptLines.stream().collect(Collectors.toMap(
                ProcurementReceiptLineRecord::getComponentId, ProcurementReceiptLineRecord::getUnit,
                (left, right) -> left));
        LocalDateTime now = LocalDateTime.now();
        componentIds.forEach(componentId -> mapper.upsertInventoryBalance(componentId, units.get(componentId), now));
        List<InventoryBalanceRecord> balances = mapper.selectInventoryBalancesForUpdate(componentIds);
        if (balances.size() != componentIds.size()) {
            throw new IllegalStateException("Inventory balance lock set is incomplete");
        }
        Map<Long, InventoryBalanceRecord> balanceByComponent = balances.stream()
                .collect(Collectors.toMap(InventoryBalanceRecord::getComponentId, Function.identity()));
        for (InventoryBalanceRecord balance : balances) {
            if (!balance.getUnit().equals(units.get(balance.getComponentId()))) {
                throw ProcurementApiException.quantityInvalid();
            }
        }

        String requestId = UUID.randomUUID().toString();
        List<Map<String, Object>> batchResults = new ArrayList<>();
        List<Map<String, Object>> movementResults = new ArrayList<>();
        boolean firstMovement = true;
        for (ProcurementReceiptLineRecord line : receiptLines) {
            ProcurementOrderLineVO orderLine = ordersById.get(line.getOrderLineId());
            InventoryBalanceRecord balance = balanceByComponent.get(line.getComponentId());
            BigDecimal before = balance.getOnHandQuantity();
            BigDecimal after = before.add(line.getQuantity()).setScale(3, RoundingMode.UNNECESSARY);

            InventoryBatchRecord batch = batch(line, orderLine.getEffectiveUnitCost(), now);
            mapper.insertInventoryBatch(batch);
            mapper.updateInventoryBalance(line.getComponentId(), line.getQuantity(), now);

            InventoryMovementRecord movement = movement(line, batch, before, after, requestId, now);
            mapper.insertInventoryMovement(movement);
            if (mapper.updateOrderLineReceived(orderLine.getId(), orderLine.getVersion(), line.getQuantity()) != 1) {
                throw ProcurementApiException.receiptOverQuantity(orderLine.getId(),
                        orderLine.getRemainingQuantity(), line.getQuantity());
            }
            balance.setOnHandQuantity(after);
            batchResults.add(batchResult(batch));
            movementResults.add(movementResult(movement));
            if (firstMovement) {
                firstMovement = false;
                failIfConfigured("AFTER_FIRST_MOVEMENT", testFaultPoint);
            }
        }

        SysUser actor = userMapper.selectById(access.userId());
        if (actor == null || actor.getStatus() == null || actor.getStatus() != 1) {
            throw ProcurementApiException.receiptDenied();
        }
        if (mapper.confirmReceipt(receiptId, receipt.getVersion(), actor.getId(), actor.getUsername(), now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectReceipt(receiptId).getVersion());
        }
        String orderStatus = mapper.countOrderLinesWithRemaining(order.getId()) == 0
                ? "RECEIVED" : "PARTIALLY_RECEIVED";
        if (mapper.updateOrderReceiptStatus(order.getId(), order.getVersion(), order.getStatus(), orderStatus,
                actor.getId(), now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectOrder(order.getId()).getVersion());
        }
        support.audit(requestId, "CONFIRM_RECEIPT", "PROCUREMENT_RECEIPT", receiptId,
                "DRAFT", "CONFIRMED", null,
                Map.of("orderId", order.getId(), "orderStatus", orderStatus));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("receiptId", receiptId);
        result.put("receiptNo", receipt.getReceiptNo());
        result.put("receiptStatus", "CONFIRMED");
        result.put("orderId", order.getId());
        result.put("orderStatus", orderStatus);
        result.put("orderVersion", order.getVersion() + 1);
        result.put("batches", batchResults);
        result.put("movements", movementResults);
        return result;
    }

    private List<ProcurementReceiptLineRecord> validatedLines(Long orderId,
                                                               List<ProcurementReceiptLineDTO> requests) {
        if (requests == null || requests.isEmpty()) {
            throw ProcurementApiException.receiptLinesRequired();
        }
        List<ProcurementOrderLineVO> orderLines = mapper.selectOrderLines(orderId);
        Map<Long, ProcurementOrderLineVO> byId = orderLines.stream()
                .collect(Collectors.toMap(ProcurementOrderLineVO::getId, Function.identity()));
        Set<Long> unique = new HashSet<>();
        List<ProcurementReceiptLineRecord> lines = new ArrayList<>();
        for (ProcurementReceiptLineDTO request : requests) {
            if (request == null || request.orderLineId() == null || !unique.add(request.orderLineId())) {
                throw ProcurementApiException.invalid();
            }
            ProcurementOrderLineVO orderLine = byId.get(request.orderLineId());
            if (orderLine == null) {
                throw ProcurementApiException.invalid();
            }
            BigDecimal quantity = quantity(request.quantity());
            if (quantity.compareTo(orderLine.getRemainingQuantity()) > 0) {
                throw ProcurementApiException.receiptOverQuantity(orderLine.getId(),
                        orderLine.getRemainingQuantity(), quantity);
            }
            validateDates(request.productionDate(), request.expiryDate());
            ProcurementReceiptLineRecord line = new ProcurementReceiptLineRecord();
            line.setOrderLineId(orderLine.getId());
            line.setComponentId(orderLine.getComponentId());
            line.setQuantity(quantity);
            line.setUnit(orderLine.getUnit());
            line.setSupplierLotNo(optionalText(request.supplierLotNo(), 120));
            line.setProductionDate(request.productionDate());
            line.setExpiryDate(request.expiryDate());
            line.setLocationNote(optionalText(request.locationNote(), 200));
            line.setQualityNote(optionalText(request.qualityNote(), 500));
            lines.add(line);
        }
        return lines;
    }

    private void validateConfirmLines(List<ProcurementReceiptLineRecord> receiptLines,
                                      Map<Long, ProcurementOrderLineVO> orderLines) {
        for (ProcurementReceiptLineRecord receiptLine : receiptLines) {
            ProcurementOrderLineVO orderLine = orderLines.get(receiptLine.getOrderLineId());
            if (orderLine == null || !receiptLine.getComponentId().equals(orderLine.getComponentId())
                    || !receiptLine.getUnit().equals(orderLine.getUnit())
                    || orderLine.getEffectiveUnitCost() == null) {
                throw ProcurementApiException.invalidTransition();
            }
            if (receiptLine.getQuantity().compareTo(orderLine.getRemainingQuantity()) > 0) {
                throw ProcurementApiException.receiptOverQuantity(orderLine.getId(),
                        orderLine.getRemainingQuantity(), receiptLine.getQuantity());
            }
        }
    }

    private InventoryBatchRecord batch(ProcurementReceiptLineRecord line, BigDecimal unitCost,
                                       LocalDateTime now) {
        InventoryBatchRecord batch = new InventoryBatchRecord();
        batch.setBatchNo(number("LOT"));
        batch.setComponentId(line.getComponentId());
        batch.setSourceReceiptLineId(line.getId());
        batch.setQuantity(line.getQuantity());
        batch.setUnit(line.getUnit());
        batch.setUnitCost(unitCost);
        batch.setSupplierLotNo(line.getSupplierLotNo());
        batch.setProductionDate(line.getProductionDate());
        batch.setExpiryDate(line.getExpiryDate());
        batch.setLocationNote(line.getLocationNote());
        batch.setQualityNote(line.getQualityNote());
        batch.setCreatedBy(access.userId());
        batch.setCreateTime(now);
        return batch;
    }

    private InventoryMovementRecord movement(ProcurementReceiptLineRecord line, InventoryBatchRecord batch,
                                              BigDecimal before, BigDecimal after, String requestId,
                                              LocalDateTime now) {
        InventoryMovementRecord movement = new InventoryMovementRecord();
        movement.setMovementNo(number("MOV"));
        movement.setComponentId(line.getComponentId());
        movement.setBatchId(batch.getId());
        movement.setSourceId(line.getId());
        movement.setQuantityDelta(line.getQuantity());
        movement.setBalanceBefore(before);
        movement.setBalanceAfter(after);
        movement.setUnit(line.getUnit());
        movement.setUnitCost(batch.getUnitCost());
        movement.setOperatorId(access.userId());
        movement.setOccurredAt(now);
        movement.setRequestId(requestId);
        return movement;
    }

    private Map<String, Object> batchResult(InventoryBatchRecord batch) {
        return Map.of("batchId", batch.getId(), "batchNo", batch.getBatchNo(),
                "componentId", batch.getComponentId(), "quantity", batch.getQuantity(),
                "unit", batch.getUnit(), "unitCost", batch.getUnitCost());
    }

    private Map<String, Object> movementResult(InventoryMovementRecord movement) {
        return Map.of("movementId", movement.getId(), "movementNo", movement.getMovementNo(),
                "quantityDelta", movement.getQuantityDelta(), "balanceBefore", movement.getBalanceBefore(),
                "balanceAfter", movement.getBalanceAfter());
    }

    private ProcurementOrderRecord visibleOrder(Long id) {
        access.requireCreate();
        ProcurementOrderRecord order = mapper.selectOrder(id);
        if (order == null || !access.canView(order)) {
            throw ProcurementApiException.orderNotFound();
        }
        return order;
    }

    private ProcurementOrderRecord lockedOrder(Long id) {
        ProcurementOrderRecord order = mapper.selectOrderForUpdate(id);
        if (order == null) {
            throw ProcurementApiException.orderNotFound();
        }
        return order;
    }

    private ProcurementReceiptRecord visibleReceipt(Long id) {
        access.requireCreate();
        ProcurementReceiptRecord receipt = requiredReceipt(id);
        ProcurementOrderRecord order = mapper.selectOrder(receipt.getOrderId());
        if (order == null || !access.canView(order)) {
            throw ProcurementApiException.receiptNotFound();
        }
        return receipt;
    }

    private ProcurementReceiptRecord requiredReceipt(Long id) {
        ProcurementReceiptRecord receipt = mapper.selectReceipt(id);
        if (receipt == null) {
            throw ProcurementApiException.receiptNotFound();
        }
        return receipt;
    }

    private ProcurementReceiptRecord requiredLockedReceipt(Long id, Long orderId) {
        ProcurementReceiptRecord receipt = mapper.selectReceiptForUpdate(id);
        if (receipt == null || !orderId.equals(receipt.getOrderId())) {
            throw ProcurementApiException.receiptNotFound();
        }
        return receipt;
    }

    private ProcurementReceiptVO receiptView(Long id) {
        ProcurementReceiptVO view = mapper.selectReceiptView(id);
        if (view == null) {
            throw ProcurementApiException.receiptNotFound();
        }
        view.setLines(mapper.selectReceiptLineViews(id));
        return view;
    }

    private void touchOrder(ProcurementOrderRecord order, LocalDateTime now) {
        if (mapper.touchOrderVersion(order.getId(), order.getVersion(), order.getStatus(), access.userId(), now) != 1) {
            throw ProcurementApiException.versionConflict(mapper.selectOrder(order.getId()).getVersion());
        }
    }

    private void requireReceivable(ProcurementOrderRecord order) {
        if (!RECEIVABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw ProcurementApiException.invalidTransition();
        }
    }

    private void requireDraft(ProcurementReceiptRecord receipt) {
        if ("CONFIRMED".equals(receipt.getStatus())) {
            throw ProcurementApiException.receiptAlreadyConfirmed();
        }
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw ProcurementApiException.invalidTransition();
        }
    }

    private void requireVersions(ProcurementOrderRecord order, Integer orderVersion,
                                 ProcurementReceiptRecord receipt, Integer receiptVersion) {
        requireOrderVersion(order, orderVersion);
        if (receiptVersion == null) {
            throw ProcurementApiException.invalid();
        }
        if (!receipt.getVersion().equals(receiptVersion)) {
            throw ProcurementApiException.versionConflict(receipt.getVersion());
        }
    }

    private void requireOrderVersion(ProcurementOrderRecord order, Integer version) {
        if (version == null) {
            throw ProcurementApiException.invalid();
        }
        if (!order.getVersion().equals(version)) {
            throw ProcurementApiException.versionConflict(order.getVersion());
        }
    }

    private LocalDateTime receivedAt(OffsetDateTime value) {
        if (value == null || value.isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw ProcurementApiException.receiptDateInvalid();
        }
        return value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private void validateDates(LocalDate productionDate, LocalDate expiryDate) {
        if (productionDate != null && expiryDate != null && expiryDate.isBefore(productionDate)) {
            throw ProcurementApiException.receiptDateInvalid();
        }
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || Math.max(0, value.stripTrailingZeros().scale()) > 3) {
            throw ProcurementApiException.quantityInvalid();
        }
        return value.setScale(3, RoundingMode.UNNECESSARY);
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

    private String number(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000);
        return prefix + "-" + date + "-" + String.format(Locale.ROOT, "%06d", suffix);
    }

    private void failIfConfigured(String point, String requestedPoint) {
        String configured = environment.getProperty("usnhub.procurement.test-fault-point");
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")
                && (point.equals(configured) || point.equals(requestedPoint))) {
            throw ProcurementApiException.transactionFailed();
        }
    }
}
