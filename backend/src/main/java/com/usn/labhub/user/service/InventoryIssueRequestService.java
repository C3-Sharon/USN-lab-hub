package com.usn.labhub.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueRequestCreateDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueRequestLineDTO;
import com.usn.labhub.user.domain.dto.inventory.InventoryIssueRequestUpdateDTO;
import com.usn.labhub.user.domain.entity.SysUser;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueComponentRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueLineRecord;
import com.usn.labhub.user.domain.entity.inventory.InventoryIssueRequestRecord;
import com.usn.labhub.user.domain.entity.project.ProjectAccessRecord;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssueLineVO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssuePageVO;
import com.usn.labhub.user.domain.vo.inventory.InventoryIssueRequestVO;
import com.usn.labhub.user.inventory.InventoryIssueAccessPolicy;
import com.usn.labhub.user.inventory.InventoryIssueApiException;
import com.usn.labhub.user.inventory.InventoryIssueSupport;
import com.usn.labhub.user.mapper.InventoryIssueMapper;
import com.usn.labhub.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryIssueRequestService {
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "ISSUED", "CANCELED", "EXPIRED");
    private static final Map<String, String> SORTS = Map.of(
            "createTime", "r.create_time", "updateTime", "r.update_time",
            "submittedAt", "r.submitted_at", "expiresAt", "r.expires_at",
            "requestNo", "r.request_no", "status", "r.status");

    private final InventoryIssueMapper mapper;
    private final SysUserMapper userMapper;
    private final InventoryIssueAccessPolicy access;
    private final InventoryIssueSupport support;
    private final ObjectMapper objectMapper;

    public InventoryIssueRequestService(InventoryIssueMapper mapper, SysUserMapper userMapper,
                                        InventoryIssueAccessPolicy access, InventoryIssueSupport support,
                                        ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.access = access;
        this.support = support;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> create(String idempotencyKey, InventoryIssueRequestCreateDTO request) {
        access.requireCreate();
        return support.idempotent("POST:/api/inventory-issue-requests", idempotencyKey, request,
                () -> createValue(request));
    }

    public InventoryIssuePageVO list(int page, int pageSize, String status, Long requesterId,
                                     Long projectId, Long componentId, LocalDateTime submittedFrom,
                                     LocalDateTime submittedTo, String keyword, String sortBy,
                                     String sortOrder) {
        access.requireCreate();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw InventoryIssueApiException.invalid();
        }
        String normalizedStatus = enumOrNull(status, STATUSES);
        String search = keyword(keyword, 80);
        String sortColumn = SORTS.get(sortBy == null ? "updateTime" : sortBy);
        if (sortColumn == null) {
            throw InventoryIssueApiException.invalid();
        }
        String order = sortOrder == null ? "DESC" : sortOrder.toUpperCase(Locale.ROOT);
        if (!Set.of("ASC", "DESC").contains(order)) {
            throw InventoryIssueApiException.invalid();
        }
        Long userId = access.userId();
        boolean viewAll = access.canViewAll();
        long total = mapper.countRequests(userId, viewAll, normalizedStatus, projectId, requesterId,
                componentId, submittedFrom, submittedTo, search);
        List<InventoryIssueRequestVO> items = mapper.selectRequests(userId, viewAll, normalizedStatus,
                projectId, requesterId, componentId, submittedFrom, submittedTo, search,
                sortColumn, order, (page - 1) * pageSize, pageSize);
        items.forEach(this::decorate);
        return new InventoryIssuePageVO(total, page, pageSize, items);
    }

    public InventoryIssueRequestVO detail(Long id) {
        visibleRequest(id);
        InventoryIssueRequestVO view = mapper.selectRequestView(id);
        if (view == null) {
            throw InventoryIssueApiException.requestNotFound();
        }
        view.setLines(mapper.selectLines(id));
        decorate(view);
        return view;
    }

    @Transactional
    public InventoryIssueRequestVO update(Long id, InventoryIssueRequestUpdateDTO request) {
        InventoryIssueRequestRecord current = visibleRequest(id);
        access.requireEdit(current);
        if (!"DRAFT".equals(current.getStatus())) {
            throw InventoryIssueApiException.invalidTransition();
        }
        if (!current.getVersion().equals(request.version())) {
            throw InventoryIssueApiException.versionConflict(current.getVersion());
        }
        ProjectAccessRecord project = access.requireVisibleProject(request.projectId());
        List<InventoryIssueLineRecord> lines = calculate(request.lines());

        current.setTitle(requiredText(request.title(), 120));
        current.setPurpose(requiredText(request.purpose(), 500));
        current.setProjectId(request.projectId());
        current.setProjectName(project == null ? null : project.getProjectName());
        current.setUpdatedBy(access.userId());
        current.setUpdateTime(LocalDateTime.now());
        if (mapper.updateDraftRequest(current) != 1) {
            InventoryIssueRequestRecord latest = mapper.selectRequest(id);
            throw InventoryIssueApiException.versionConflict(latest == null
                    ? current.getVersion() : latest.getVersion());
        }
        mapper.deleteLines(id);
        insertLines(id, lines);
        support.audit(UUID.randomUUID().toString(), "UPDATE_DRAFT", "INVENTORY_ISSUE_REQUEST", id,
                "DRAFT", "DRAFT", null, Map.of("version", request.version()));
        return detail(id);
    }

    private Map<String, Object> createValue(InventoryIssueRequestCreateDTO request) {
        ProjectAccessRecord project = access.requireVisibleProject(request.projectId());
        List<InventoryIssueLineRecord> lines = calculate(request.lines());
        Long actorId = access.userId();
        SysUser actor = userMapper.selectById(actorId);
        if (actor == null) {
            throw InventoryIssueApiException.operationDenied();
        }
        LocalDateTime now = LocalDateTime.now();
        InventoryIssueRequestRecord issue = new InventoryIssueRequestRecord();
        issue.setRequestNo(requestNo());
        issue.setTitle(requiredText(request.title(), 120));
        issue.setPurpose(requiredText(request.purpose(), 500));
        issue.setRequesterId(actorId);
        issue.setRequesterName(actor.getUsername());
        issue.setProjectId(request.projectId());
        issue.setProjectName(project == null ? null : project.getProjectName());
        issue.setCreatedBy(actorId);
        issue.setUpdatedBy(actorId);
        issue.setCreateTime(now);
        issue.setUpdateTime(now);
        mapper.insertRequest(issue);
        insertLines(issue.getId(), lines);
        support.audit(UUID.randomUUID().toString(), "CREATE_DRAFT", "INVENTORY_ISSUE_REQUEST",
                issue.getId(), "DRAFT", "DRAFT", null, Map.of("requestNo", issue.getRequestNo()));
        return support.asMap(detail(issue.getId()));
    }

    private List<InventoryIssueLineRecord> calculate(List<InventoryIssueRequestLineDTO> requests) {
        if (requests == null || requests.isEmpty()) {
            throw InventoryIssueApiException.linesRequired();
        }
        Set<Long> componentIds = new HashSet<>();
        List<InventoryIssueLineRecord> lines = new ArrayList<>();
        for (InventoryIssueRequestLineDTO request : requests) {
            if (request == null || request.componentId() == null
                    || !componentIds.add(request.componentId())) {
                throw InventoryIssueApiException.componentInvalid();
            }
            BigDecimal quantity = quantity(request.requestedQuantity());
            InventoryIssueComponentRecord component = mapper.selectComponentForIssue(request.componentId());
            if (!componentVisible(component)) {
                throw InventoryIssueApiException.componentInvalid();
            }
            InventoryIssueLineRecord line = new InventoryIssueLineRecord();
            line.setComponentId(component.getId());
            line.setComponentName(component.getName());
            line.setManufacturerName(component.getManufacturerName());
            line.setMpn(component.getMpn());
            line.setUnit(component.getBaseUnit());
            line.setRequestedQuantity(quantity);
            lines.add(line);
        }
        return lines;
    }

    private void insertLines(Long requestId, List<InventoryIssueLineRecord> lines) {
        for (int index = 0; index < lines.size(); index++) {
            InventoryIssueLineRecord line = lines.get(index);
            line.setRequestId(requestId);
            line.setLineNo(index + 1);
            mapper.insertLine(line);
        }
    }

    private InventoryIssueRequestRecord visibleRequest(Long id) {
        InventoryIssueRequestRecord request = mapper.selectRequest(id);
        if (request == null || !access.canView(request)) {
            throw InventoryIssueApiException.requestNotFound();
        }
        return request;
    }

    private void decorate(InventoryIssueRequestVO view) {
        view.setApprovalReasons(parseReasons(view.getApprovalReasonsJson()));
        if (!access.canViewCost()) {
            view.setEstimatedAmount(null);
            if (view.getLines() != null) {
                view.getLines().forEach(line -> line.setEstimatedAmount(null));
            }
        }
    }

    private List<String> parseReasons(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot parse inventory issue approval reasons", exception);
        }
    }

    private boolean componentVisible(InventoryIssueComponentRecord component) {
        if (component == null || "INACTIVE".equals(component.getStatus())) {
            return false;
        }
        if ("ACTIVE".equals(component.getStatus())) {
            return true;
        }
        Set<String> roles = UserContext.getRoles();
        return roles.contains("SYSTEM_ADMIN") || roles.contains("STOCK_KEEPER")
                || roles.contains("TEACHER");
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || precisionScale(value) > 3) {
            throw InventoryIssueApiException.quantityInvalid();
        }
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }

    private int precisionScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private String requiredText(String value, int max) {
        String normalized = optionalText(value, max);
        if (normalized == null || normalized.length() < 2) {
            throw InventoryIssueApiException.invalid();
        }
        return normalized;
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

    private String keyword(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw InventoryIssueApiException.invalid();
        }
        return normalized;
    }

    private String enumOrNull(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw InventoryIssueApiException.invalid();
        }
        return normalized;
    }

    private String requestNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000);
        return "ISS-" + date + "-" + String.format(Locale.ROOT, "%06d", suffix);
    }
}
