package com.usn.labhub.user.experiment;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class ExperimentApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;
    private final Object data;

    private ExperimentApiException(HttpStatus status, String reason, String message, Object data) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.data = data;
    }

    public static ExperimentApiException invalid() {
        return new ExperimentApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "参数错误", null);
    }

    public static ExperimentApiException contentInvalid(Object errors) {
        return new ExperimentApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_CONTENT_INVALID",
                "实验内容不符合模板版本", Map.of("errors", errors));
    }

    public static ExperimentApiException recordNotFound() {
        return new ExperimentApiException(HttpStatus.NOT_FOUND, "EXPERIMENT_RECORD_NOT_FOUND", "实验记录不存在", null);
    }

    public static ExperimentApiException revisionNotFound() {
        return new ExperimentApiException(HttpStatus.NOT_FOUND, "EXPERIMENT_REVISION_NOT_FOUND", "实验修订不存在", null);
    }

    public static ExperimentApiException operationDenied() {
        return new ExperimentApiException(HttpStatus.FORBIDDEN, "EXPERIMENT_OPERATION_DENIED", "无权修改该实验记录", null);
    }

    public static ExperimentApiException feedbackDenied() {
        return new ExperimentApiException(HttpStatus.FORBIDDEN, "FEEDBACK_OPERATION_DENIED", "无权评审该实验记录", null);
    }

    public static ExperimentApiException templateMismatch() {
        return new ExperimentApiException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_MISMATCH",
                "模板版本与项目或学习单元不一致", null);
    }

    public static ExperimentApiException invalidTransition() {
        return new ExperimentApiException(HttpStatus.CONFLICT, "EXPERIMENT_INVALID_TRANSITION",
                "实验状态转换非法", null);
    }

    public static ExperimentApiException revisionImmutable() {
        return new ExperimentApiException(HttpStatus.CONFLICT, "EXPERIMENT_REVISION_IMMUTABLE",
                "该实验修订不可修改", null);
    }

    public static ExperimentApiException versionConflict(int currentVersion) {
        return new ExperimentApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "数据已被更新，请刷新后重试", Map.of("currentVersion", currentVersion));
    }

    public static ExperimentApiException idempotencyReused() {
        return new ExperimentApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "幂等键已用于不同请求", null);
    }

    public static ExperimentApiException projectConversionDenied() {
        return new ExperimentApiException(HttpStatus.FORBIDDEN, "PROJECT_CONVERSION_DENIED",
                "无权将该实验记录转换为项目", null);
    }

    public static ExperimentApiException projectAlreadyConverted() {
        return new ExperimentApiException(HttpStatus.CONFLICT, "PROJECT_ALREADY_CONVERTED",
                "该实验记录已经转换为项目", null);
    }

    public static ExperimentApiException projectCodeDuplicate() {
        return new ExperimentApiException(HttpStatus.CONFLICT, "PROJECT_CODE_DUPLICATE",
                "项目编号已存在", null);
    }
}
