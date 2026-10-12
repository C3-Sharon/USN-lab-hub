package com.usn.labhub.user.learning.template;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ExperimentTemplateApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;

    private ExperimentTemplateApiException(HttpStatus status, String reason, String message) {
        super(message);
        this.status = status;
        this.reason = reason;
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
}
