package com.usn.labhub.user.procurement;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class ProcurementApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;
    private final Map<String, Object> data;

    private ProcurementApiException(HttpStatus status, String reason, String message) {
        this(status, reason, message, null);
    }

    private ProcurementApiException(HttpStatus status, String reason, String message,
                                    Map<String, Object> data) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.data = data;
    }

    public static ProcurementApiException invalid() {
        return new ProcurementApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "参数错误");
    }

    public static ProcurementApiException amountInvalid() {
        return new ProcurementApiException(HttpStatus.BAD_REQUEST, "PROCUREMENT_AMOUNT_INVALID", "采购金额非法");
    }

    public static ProcurementApiException quantityInvalid() {
        return new ProcurementApiException(HttpStatus.BAD_REQUEST, "PROCUREMENT_QUANTITY_INVALID", "采购数量非法");
    }

    public static ProcurementApiException linesRequired() {
        return new ProcurementApiException(HttpStatus.BAD_REQUEST, "PROCUREMENT_LINES_REQUIRED", "采购明细不能为空");
    }

    public static ProcurementApiException componentInvalid() {
        return new ProcurementApiException(HttpStatus.BAD_REQUEST, "PROCUREMENT_COMPONENT_INVALID",
                "元器件不存在、不可见或状态不允许采购");
    }

    public static ProcurementApiException operationDenied() {
        return new ProcurementApiException(HttpStatus.FORBIDDEN, "PROCUREMENT_OPERATION_DENIED", "无采购操作权限");
    }

    public static ProcurementApiException approvalDenied() {
        return new ProcurementApiException(HttpStatus.FORBIDDEN, "PROCUREMENT_APPROVAL_DENIED", "无采购审批权限");
    }

    public static ProcurementApiException selfApproval() {
        return new ProcurementApiException(HttpStatus.FORBIDDEN, "PROCUREMENT_SELF_APPROVAL", "不能审批本人采购");
    }

    public static ProcurementApiException policyDenied() {
        return new ProcurementApiException(HttpStatus.FORBIDDEN, "PROCUREMENT_POLICY_DENIED", "无采购策略管理权限");
    }

    public static ProcurementApiException projectNotFound() {
        return new ProcurementApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在或不可见");
    }

    public static ProcurementApiException orderNotFound() {
        return new ProcurementApiException(HttpStatus.NOT_FOUND, "PROCUREMENT_ORDER_NOT_FOUND",
                "采购单不存在或不可见");
    }

    public static ProcurementApiException invalidTransition() {
        return new ProcurementApiException(HttpStatus.CONFLICT, "PROCUREMENT_INVALID_TRANSITION",
                "当前采购单状态不允许该操作");
    }

    public static ProcurementApiException versionConflict(Integer currentVersion) {
        return new ProcurementApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "数据已被更新，请刷新后重试",
                Map.of("currentVersion", currentVersion));
    }

    public static ProcurementApiException idempotencyReused() {
        return new ProcurementApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同请求");
    }

    public static ProcurementApiException dutyConflict() {
        return new ProcurementApiException(HttpStatus.CONFLICT, "PROCUREMENT_DUTY_CONFLICT", "采购职责分离冲突");
    }

    public static ProcurementApiException platformOrderDuplicate() {
        return new ProcurementApiException(HttpStatus.CONFLICT, "PROCUREMENT_PLATFORM_ORDER_DUPLICATE",
                "平台订单号已存在");
    }

    public static ProcurementApiException policyChanged(Integer currentVersion) {
        return new ProcurementApiException(HttpStatus.CONFLICT, "PROCUREMENT_POLICY_CHANGED", "采购策略已更新",
                Map.of("currentPolicyVersion", currentVersion));
    }
}
