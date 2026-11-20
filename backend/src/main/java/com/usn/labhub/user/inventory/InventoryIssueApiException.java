package com.usn.labhub.user.inventory;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

@Getter
public class InventoryIssueApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;
    private final Map<String, Object> data;

    private InventoryIssueApiException(HttpStatus status, String reason, String message) {
        this(status, reason, message, null);
    }

    private InventoryIssueApiException(HttpStatus status, String reason, String message,
                                       Map<String, Object> data) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.data = data;
    }

    public static InventoryIssueApiException invalid() {
        return new InventoryIssueApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "参数错误");
    }

    public static InventoryIssueApiException linesRequired() {
        return new InventoryIssueApiException(HttpStatus.BAD_REQUEST, "ISSUE_LINES_REQUIRED", "领用明细不能为空");
    }

    public static InventoryIssueApiException quantityInvalid() {
        return new InventoryIssueApiException(HttpStatus.BAD_REQUEST, "ISSUE_QUANTITY_INVALID", "领用数量非法");
    }

    public static InventoryIssueApiException componentInvalid() {
        return new InventoryIssueApiException(HttpStatus.BAD_REQUEST, "ISSUE_COMPONENT_INVALID",
                "元器件不存在、不可见或状态不允许领用");
    }

    public static InventoryIssueApiException policyInvalid() {
        return new InventoryIssueApiException(HttpStatus.BAD_REQUEST, "ISSUE_POLICY_INVALID", "领用策略参数非法");
    }

    public static InventoryIssueApiException policyOverrideInvalid() {
        return new InventoryIssueApiException(HttpStatus.BAD_REQUEST, "ISSUE_POLICY_OVERRIDE_INVALID",
                "领用策略元器件覆盖项非法");
    }

    public static InventoryIssueApiException operationDenied() {
        return new InventoryIssueApiException(HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_OPERATION_DENIED",
                "无领用操作权限");
    }

    public static InventoryIssueApiException policyDenied() {
        return new InventoryIssueApiException(HttpStatus.FORBIDDEN, "INVENTORY_POLICY_DENIED",
                "无领用策略管理权限");
    }

    public static InventoryIssueApiException requestNotFound() {
        return new InventoryIssueApiException(HttpStatus.NOT_FOUND, "INVENTORY_ISSUE_REQUEST_NOT_FOUND",
                "领用申请不存在或不可见");
    }

    public static InventoryIssueApiException projectNotFound() {
        return new InventoryIssueApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在或不可见");
    }

    public static InventoryIssueApiException invalidTransition() {
        return new InventoryIssueApiException(HttpStatus.CONFLICT, "INVENTORY_ISSUE_INVALID_TRANSITION",
                "当前领用申请状态不允许该操作");
    }

    public static InventoryIssueApiException versionConflict(Integer currentVersion) {
        return new InventoryIssueApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "数据已被更新，请刷新后重试",
                Map.of("currentVersion", currentVersion));
    }

    public static InventoryIssueApiException idempotencyReused() {
        return new InventoryIssueApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "幂等键已用于不同请求");
    }

    public static InventoryIssueApiException approvalDenied() {
        return new InventoryIssueApiException(HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_APPROVAL_DENIED",
                "无领用审批权限");
    }

    public static InventoryIssueApiException selfApproval() {
        return new InventoryIssueApiException(HttpStatus.FORBIDDEN, "INVENTORY_ISSUE_SELF_APPROVAL",
                "不能审批本人申请");
    }

    public static InventoryIssueApiException insufficientStock(Long componentId,
                                                               BigDecimal requestedQuantity,
                                                               BigDecimal availableQuantity) {
        return new InventoryIssueApiException(HttpStatus.CONFLICT, "INVENTORY_INSUFFICIENT_STOCK",
                "可用库存不足", Map.of("componentId", componentId,
                "requestedQuantity", requestedQuantity, "availableQuantity", availableQuantity));
    }

    public static InventoryIssueApiException concurrentConflict() {
        return new InventoryIssueApiException(HttpStatus.CONFLICT, "INVENTORY_CONCURRENT_CONFLICT",
                "库存或申请已变化，请刷新后重试");
    }

    public static InventoryIssueApiException reservationExpired() {
        return new InventoryIssueApiException(HttpStatus.CONFLICT, "INVENTORY_RESERVATION_EXPIRED",
                "领用预占已过期，请重新申请");
    }

    public static InventoryIssueApiException reservationNotActive() {
        return new InventoryIssueApiException(HttpStatus.CONFLICT, "INVENTORY_RESERVATION_NOT_ACTIVE",
                "领用预占已消费或释放");
    }
}
