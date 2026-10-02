package com.usn.labhub.user.project;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ProjectApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;
    private final Object data;

    public ProjectApiException(HttpStatus status, String reason, String message) {
        this(status, reason, message, null);
    }

    public ProjectApiException(HttpStatus status, String reason, String message, Object data) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.data = data;
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

    public static ProjectApiException invalidParameter() {
        return new ProjectApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "参数错误");
    }

    public static ProjectApiException milestoneNotFound() {
        return new ProjectApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "里程碑不存在");
    }

    public static ProjectApiException invalidMilestoneReference() {
        return new ProjectApiException(HttpStatus.BAD_REQUEST, "MILESTONE_NOT_FOUND", "里程碑不存在或不属于该项目");
    }

    public static ProjectApiException milestoneInvalidTransition() {
        return new ProjectApiException(HttpStatus.CONFLICT, "MILESTONE_INVALID_TRANSITION", "里程碑状态转换非法");
    }

    public static ProjectApiException paused() {
        return new ProjectApiException(HttpStatus.CONFLICT, "PROJECT_PAUSED", "暂停项目不可修改");
    }

    public static ProjectApiException completed() {
        return new ProjectApiException(HttpStatus.CONFLICT, "PROJECT_COMPLETED", "已完成项目不可修改");
    }

    public static ProjectApiException archivedProject() {
        return new ProjectApiException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", "归档项目不可修改");
    }

    public static ProjectApiException taskNotFound() {
        return new ProjectApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
    }

    public static ProjectApiException assigneeNotMember() {
        return new ProjectApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_NOT_MEMBER", "负责人不是项目成员");
    }

    public static ProjectApiException blockReasonRequired() {
        return new ProjectApiException(HttpStatus.BAD_REQUEST, "BLOCK_REASON_REQUIRED", "阻塞原因必填");
    }

    public static ProjectApiException taskInvalidTransition() {
        return new ProjectApiException(HttpStatus.CONFLICT, "TASK_INVALID_TRANSITION", "任务状态转换非法");
    }

    public static ProjectApiException versionConflict(Long taskId, Integer currentVersion) {
        return new ProjectApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "任务已被他人更新，请刷新后重试",
                java.util.Map.of("taskId", taskId, "currentVersion", currentVersion));
    }

    public static ProjectApiException memberRoleConflict() {
        return new ProjectApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER_DIFFERENT_ROLE",
                "该成员已在项目中，角色不同，请使用角色变更功能");
    }
}
