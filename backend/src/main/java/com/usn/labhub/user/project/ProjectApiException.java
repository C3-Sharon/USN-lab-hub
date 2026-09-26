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

    public static ProjectApiException operationDenied() {
        return new ProjectApiException(HttpStatus.FORBIDDEN, "PROJECT_OPERATION_DENIED", "无权执行该项目操作");
    }

    public static ProjectApiException userNotFound() {
        return new ProjectApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "成员不存在");
    }

    public static ProjectApiException archived() {
        return new ProjectApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "归档项目不能变更成员");
    }

    public static ProjectApiException memberRoleConflict() {
        return new ProjectApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER_DIFFERENT_ROLE",
                "该成员已在项目中，角色不同，请使用角色变更功能");
    }
}
