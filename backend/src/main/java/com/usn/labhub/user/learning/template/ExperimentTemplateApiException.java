package com.usn.labhub.user.learning.template;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ExperimentTemplateApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;
    private final Object data;

    private ExperimentTemplateApiException(HttpStatus status, String reason, String message) {
        this(status, reason, message, null);
    }

    private ExperimentTemplateApiException(HttpStatus status, String reason, String message, Object data) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.data = data;
    }

    public static ExperimentTemplateApiException invalidRequest() {
        return new ExperimentTemplateApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "模板请求参数不合法");
    }

    public static ExperimentTemplateApiException accessDenied() {
        return new ExperimentTemplateApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问实验模板");
    }

    public static ExperimentTemplateApiException operationDenied() {
        return new ExperimentTemplateApiException(HttpStatus.FORBIDDEN, "TEMPLATE_OPERATION_DENIED", "无模板管理权限");
    }

    public static ExperimentTemplateApiException notFound() {
        return new ExperimentTemplateApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "模板不存在或不可见");
    }

    public static ExperimentTemplateApiException projectNotFound() {
        return new ExperimentTemplateApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    public static ExperimentTemplateApiException versionNotFound() {
        return new ExperimentTemplateApiException(HttpStatus.NOT_FOUND, "TEMPLATE_VERSION_NOT_FOUND",
                "模板版本不存在或不可见");
    }

    public static ExperimentTemplateApiException versionNotEditable() {
        return new ExperimentTemplateApiException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_NOT_EDITABLE",
                "该模板版本不可编辑");
    }

    public static ExperimentTemplateApiException versionNotPublishable() {
        return new ExperimentTemplateApiException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_NOT_PUBLISHABLE",
                "该模板版本不可发布");
    }

    public static ExperimentTemplateApiException versionNotDeprecatable() {
        return new ExperimentTemplateApiException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_NOT_DEPRECATABLE",
                "该模板版本不可废弃");
    }

    public static ExperimentTemplateApiException versionConflict() {
        return new ExperimentTemplateApiException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_CONFLICT",
                "模板版本已被其他操作更新，请刷新后重试");
    }

    public static ExperimentTemplateApiException schemaEmpty() {
        return new ExperimentTemplateApiException(HttpStatus.BAD_REQUEST, "SCHEMA_EMPTY",
                "Schema 为空，无法发布");
    }

    public static ExperimentTemplateApiException notSystemTemplate() {
        return new ExperimentTemplateApiException(HttpStatus.BAD_REQUEST, "TEMPLATE_NOT_SYSTEM",
                "只能从系统模板复制");
    }

    public static ExperimentTemplateApiException schemaInvalid(Object errors) {
        return new ExperimentTemplateApiException(HttpStatus.BAD_REQUEST, "SCHEMA_INVALID", "Schema 校验失败",
                java.util.Map.of("errors", errors));
    }
}
