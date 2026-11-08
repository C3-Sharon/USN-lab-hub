package com.usn.labhub.user.service;

import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderCreateDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderLineDTO;
import com.usn.labhub.user.domain.dto.procurement.ProcurementOrderUpdateDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.entity.procurement.ProcurementComponentRecord;
import com.usn.labhub.user.domain.entity.procurement.ProcurementOrderRecord;
import com.usn.labhub.user.domain.entity.project.ProjectAccessRecord;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderPageVO;
import com.usn.labhub.user.domain.vo.procurement.ProcurementOrderVO;
import com.usn.labhub.user.mapper.ProcurementMapper;
import com.usn.labhub.user.mapper.SysUserMapper;
import com.usn.labhub.user.procurement.ProcurementAccessPolicy;
import com.usn.labhub.user.procurement.ProcurementApiException;
import com.usn.labhub.user.procurement.ProcurementSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProcurementOrderService {
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "PENDING_APPROVAL", "APPROVED", "ORDERED", "PARTIALLY_RECEIVED",
            "RECEIVED", "COMPLETED", "REJECTED", "CANCELED");
    private static final Map<String, String> SORTS = Map.of(
            "createTime", "o.create_time", "updateTime", "o.update_time",
            "purchaseNo", "o.purchase_no", "payableAmount", "o.payable_amount",
            "orderedAt", "o.ordered_at");

    private final ProcurementMapper mapper;
    private final SysUserMapper userMapper;
    private final ProcurementAccessPolicy access;
    private final ProcurementSupport support;

    public ProcurementOrderService(ProcurementMapper mapper, SysUserMapper userMapper,
                                   ProcurementAccessPolicy access, ProcurementSupport support) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.access = access;
        this.support = support;
    }

    @Transactional
    public Map<String, Object> create(String idempotencyKey, ProcurementOrderCreateDTO request) {
        access.requireCreate();
        return support.idempotent("POST:/api/procurement-orders", idempotencyKey, request,
                () -> createValue(request));
    }

    public ProcurementOrderPageVO orders(int page, int pageSize, String status, Long projectId,
                                          Long requesterId, String supplierKeyword, String keyword,
                                          String sortBy, String sortOrder) {
        access.requireCreate();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw ProcurementApiException.invalid();
        }
        String normalizedStatus = enumOrNull(status, STATUSES);
        String supplier = keyword(supplierKeyword, 80);
        String search = keyword(keyword, 80);
        String sortColumn = SORTS.get(sortBy == null ? "updateTime" : sortBy);
        if (sortColumn == null) {
            throw ProcurementApiException.invalid();
        }
        String order = sortOrder == null ? "DESC" : sortOrder.toUpperCase(Locale.ROOT);
        if (!Set.of("ASC", "DESC").contains(order)) {
            throw ProcurementApiException.invalid();
        }
        Long userId = access.userId();
        boolean viewAll = access.canViewAll();
        long total = mapper.countOrders(userId, viewAll, normalizedStatus, projectId, requesterId, supplier, search);
        List<ProcurementOrderVO> list = mapper.selectOrders(userId, viewAll, normalizedStatus, projectId,
                requesterId, supplier, search, sortColumn, order, (page - 1) * pageSize, pageSize);
        list.forEach(this::decorateOrder);
        return new ProcurementOrderPageVO(total, page, pageSize, list);
    }

    public ProcurementOrderVO order(Long id) {
        ProcurementOrderRecord record = visibleOrder(id);
        return orderView(record.getId());
    }

    @Transactional
    public ProcurementOrderVO update(Long id, ProcurementOrderUpdateDTO request) {
        ProcurementOrderRecord current = visibleOrder(id);
        access.requireEdit(current);
        if (!"DRAFT".equals(current.getStatus())) {
            throw ProcurementApiException.invalidTransition();
        }
        if (!current.getVersion().equals(request.version())) {
            throw ProcurementApiException.versionConflict(current.getVersion());
        }
        ProjectAccessRecord project = access.requireWritableProject(request.projectId());
        CalculatedDraft calculated = calculate(request.lines(), request.shippingAmount(), request.discountAmount());

        current.setTitle(requiredText(request.title(), 120));
        current.setProjectId(request.projectId());
        current.setProjectName(project == null ? null : project.getProjectName());
        current.setFundingSource(optionalText(request.fundingSource(), 120));
        current.setItemSubtotal(calculated.itemSubtotal());
        current.setShippingAmount(calculated.shippingAmount());
        current.setDiscountAmount(calculated.discountAmount());
        current.setPayableAmount(calculated.payableAmount());
        current.setUpdatedBy(access.userId());
        current.setUpdateTime(LocalDateTime.now());
        if (mapper.updateDraftOrder(current) != 1) {
            ProcurementOrderRecord latest = mapper.selectOrder(id);
            throw ProcurementApiException.versionConflict(latest == null ? current.getVersion() : latest.getVersion());
        }
        mapper.deleteLines(id);
        insertLines(id, calculated.lines());
        String requestId = UUID.randomUUID().toString();
        support.audit(requestId, "UPDATE_DRAFT", id,
                Map.of("version", request.version(), "payableAmount", calculated.payableAmount()));
        return orderView(id);
    }

    private Map<String, Object> createValue(ProcurementOrderCreateDTO request) {
        ProjectAccessRecord project = access.requireWritableProject(request.projectId());
        CalculatedDraft calculated = calculate(request.lines(), request.shippingAmount(), request.discountAmount());
        Long actorId = access.userId();
        SysUser actor = userMapper.selectById(actorId);
        if (actor == null) {
            throw ProcurementApiException.operationDenied();
        }
        LocalDateTime now = LocalDateTime.now();
        ProcurementOrderRecord order = new ProcurementOrderRecord();
        order.setPurchaseNo(purchaseNo());
        order.setTitle(requiredText(request.title(), 120));
        order.setRequesterId(actorId);
        order.setRequesterName(actor.getUsername());
        order.setProjectId(request.projectId());
        order.setProjectName(project == null ? null : project.getProjectName());
        order.setFundingSource(optionalText(request.fundingSource(), 120));
        order.setItemSubtotal(calculated.itemSubtotal());
        order.setShippingAmount(calculated.shippingAmount());
        order.setDiscountAmount(calculated.discountAmount());
        order.setPayableAmount(calculated.payableAmount());
        order.setCreatedBy(actorId);
        order.setUpdatedBy(actorId);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        mapper.insertOrder(order);
        insertLines(order.getId(), calculated.lines());
        support.audit(UUID.randomUUID().toString(), "CREATE_DRAFT", order.getId(),
                Map.of("purchaseNo", order.getPurchaseNo(), "payableAmount", calculated.payableAmount()));
        return support.asMap(orderView(order.getId()));
    }

    private CalculatedDraft calculate(List<ProcurementOrderLineDTO> requests,
                                      BigDecimal shippingAmount, BigDecimal discountAmount) {
        if (requests == null || requests.isEmpty()) {
            throw ProcurementApiException.linesRequired();
        }
        BigDecimal shipping = money(shippingAmount);
        BigDecimal discount = money(discountAmount);
        Set<Long> componentIds = new HashSet<>();
        java.util.ArrayList<CalculatedLine> lines = new java.util.ArrayList<>();
        BigDecimal itemSubtotal = BigDecimal.ZERO.setScale(2);
        for (ProcurementOrderLineDTO request : requests) {
            if (request == null || request.componentId() == null || !componentIds.add(request.componentId())) {
                throw ProcurementApiException.componentInvalid();
            }
            BigDecimal quantity = quantity(request.orderedQuantity());
            BigDecimal unitPrice = unitPrice(request.unitPrice());
            ProcurementComponentRecord component = mapper.selectComponentForPurchase(request.componentId());
            if (!componentVisible(component)) {
                throw ProcurementApiException.componentInvalid();
            }
            BigDecimal lineSubtotal = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            itemSubtotal = itemSubtotal.add(lineSubtotal);
            lines.add(new CalculatedLine(component, quantity, unitPrice, lineSubtotal));
        }
        BigDecimal payable = itemSubtotal.add(shipping).subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (payable.signum() < 0) {
            throw ProcurementApiException.amountInvalid();
        }
        return new CalculatedDraft(itemSubtotal.setScale(2), shipping, discount, payable, List.copyOf(lines));
    }

    private void insertLines(Long orderId, List<CalculatedLine> lines) {
        for (int index = 0; index < lines.size(); index++) {
            CalculatedLine line = lines.get(index);
            mapper.insertLine(orderId, index + 1, line.component(), line.quantity(),
                    line.unitPrice(), line.lineSubtotal());
        }
    }

    private ProcurementOrderRecord visibleOrder(Long id) {
        ProcurementOrderRecord order = mapper.selectOrder(id);
        if (order == null || !access.canView(order)) {
            throw ProcurementApiException.orderNotFound();
        }
        return order;
    }

    private ProcurementOrderVO orderView(Long id) {
        ProcurementOrderVO view = mapper.selectOrderView(id);
        if (view == null) {
            throw ProcurementApiException.orderNotFound();
        }
        view.setLines(mapper.selectOrderLines(id));
        decorateOrder(view);
        return view;
    }

    private void decorateOrder(ProcurementOrderVO view) {
        if (view.getApprovalReasons() == null) {
            view.setApprovalReasons(List.of());
        }
    }

    private boolean componentVisible(ProcurementComponentRecord component) {
        if (component == null || "INACTIVE".equals(component.getStatus())) {
            return false;
        }
        if ("ACTIVE".equals(component.getStatus())) {
            return true;
        }
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("STOCK_KEEPER") || roles.contains("TEACHER");
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || precisionScale(value) > 2) {
            throw ProcurementApiException.amountInvalid();
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || precisionScale(value) > 3) {
            throw ProcurementApiException.quantityInvalid();
        }
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }

    private BigDecimal unitPrice(BigDecimal value) {
        if (value == null || value.signum() <= 0 || precisionScale(value) > 4) {
            throw ProcurementApiException.amountInvalid();
        }
        return value.setScale(4, RoundingMode.UNNECESSARY);
    }

    private int precisionScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private String requiredText(String value, int max) {
        String normalized = optionalText(value, max);
        if (normalized == null || normalized.length() < 2) {
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

    private String keyword(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }

    private String enumOrNull(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw ProcurementApiException.invalid();
        }
        return normalized;
    }

    private String purchaseNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000);
        return "PO-" + date + "-" + String.format(Locale.ROOT, "%06d", suffix);
    }

    private record CalculatedLine(ProcurementComponentRecord component, BigDecimal quantity,
                                  BigDecimal unitPrice, BigDecimal lineSubtotal) {
    }

    private record CalculatedDraft(BigDecimal itemSubtotal, BigDecimal shippingAmount,
                                   BigDecimal discountAmount, BigDecimal payableAmount,
                                   List<CalculatedLine> lines) {
    }
}
