package com.usn.labhub.user.project;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ProjectApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;

    public ProjectApiException(HttpStatus status, String reason, String message) {
        super(message);
        this.status = status;
        this.reason = reason;
    }

    public static ProjectApiException badRequest() {
        return new ProjectApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_REQUEST", "项目请求参数不合法");
    }

    public static ProjectApiException notFound() {
        return new ProjectApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    public static ProjectApiException duplicateCode() {
        return new ProjectApiException(HttpStatus.CONFLICT, "PROJECT_CODE_DUPLICATE", "项目编号已存在");
    }
}
