package com.usn.labhub.user.component;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ComponentCatalogApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;
    private final Map<String, Object> data;

    private ComponentCatalogApiException(HttpStatus status, String reason, String message) {
        this(status, reason, message, null);
    }

    private ComponentCatalogApiException(HttpStatus status, String reason, String message,
                                         Map<String, Object> data) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.data = data;
    }

    public static ComponentCatalogApiException invalid() {
        return new ComponentCatalogApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "参数错误");
    }

    public static ComponentCatalogApiException operationDenied() {
        return new ComponentCatalogApiException(HttpStatus.FORBIDDEN, "COMPONENT_OPERATION_DENIED",
                "无元器件目录维护权限");
    }

    public static ComponentCatalogApiException categoryNotFound() {
        return new ComponentCatalogApiException(HttpStatus.NOT_FOUND, "COMPONENT_CATEGORY_NOT_FOUND",
                "元器件分类不存在或不可见");
    }

    public static ComponentCatalogApiException manufacturerNotFound() {
        return new ComponentCatalogApiException(HttpStatus.NOT_FOUND, "COMPONENT_MANUFACTURER_NOT_FOUND",
                "制造商不存在或不可见");
    }

    public static ComponentCatalogApiException componentNotFound() {
        return new ComponentCatalogApiException(HttpStatus.NOT_FOUND, "COMPONENT_NOT_FOUND",
                "元器件型号不存在或不可见");
    }

    public static ComponentCatalogApiException categoryDuplicateCode() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "CATEGORY_DUPLICATE_CODE", "分类编码已存在");
    }

    public static ComponentCatalogApiException categoryDuplicateName() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "CATEGORY_DUPLICATE_NAME", "同级分类名称已存在");
    }

    public static ComponentCatalogApiException manufacturerDuplicate() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "MANUFACTURER_DUPLICATE_CODE",
                "制造商编码或名称已存在");
    }

    public static ComponentCatalogApiException duplicateModel(Long id, String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("existingComponentId", id);
        data.put("currentStatus", status);
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "COMPONENT_DUPLICATE_MODEL",
                "同一制造商下的型号已存在", data);
    }

    public static ComponentCatalogApiException categoryDepthExceeded() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "CATEGORY_DEPTH_EXCEEDED", "分类最多支持三级");
    }

    public static ComponentCatalogApiException categoryParentInactive() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "CATEGORY_PARENT_INACTIVE", "父分类已停用");
    }

    public static ComponentCatalogApiException categoryInUse() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "分类仍被使用");
    }

    public static ComponentCatalogApiException manufacturerInUse() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "MANUFACTURER_IN_USE", "制造商仍被使用");
    }

    public static ComponentCatalogApiException referenceInactive() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "COMPONENT_REFERENCE_INACTIVE",
                "型号引用的分类或制造商未启用");
    }

    public static ComponentCatalogApiException invalidTransition() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "COMPONENT_INVALID_TRANSITION",
                "元器件状态转换非法");
    }

    public static ComponentCatalogApiException specRequired() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "COMPONENT_SPEC_REQUIRED",
                "激活前必须存在正式规格快照");
    }

    public static ComponentCatalogApiException versionConflict(Integer version) {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "数据已被更新，请刷新后重试", Map.of("currentVersion", version));
    }

    public static ComponentCatalogApiException idempotencyReused() {
        return new ComponentCatalogApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "幂等键已用于不同请求");
    }
}
