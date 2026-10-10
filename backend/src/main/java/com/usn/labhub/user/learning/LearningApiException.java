package com.usn.labhub.user.learning;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LearningApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;

    private LearningApiException(HttpStatus status, String reason, String message) {
        super(message);
        this.status = status;
        this.reason = reason;
    }

    public static LearningApiException invalidParameter() {
        return new LearningApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "参数错误");
    }

    public static LearningApiException operationDenied() {
        return new LearningApiException(HttpStatus.FORBIDDEN, "LEARNING_OPERATION_DENIED", "无学习路线管理权限");
    }

    public static LearningApiException accessDenied() {
        return new LearningApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问学习实验台");
    }

    public static LearningApiException roadmapNotFound() {
        return new LearningApiException(HttpStatus.NOT_FOUND, "LEARNING_ROADMAP_NOT_FOUND", "学习路线不存在");
    }

    public static LearningApiException stageNotFound() {
        return new LearningApiException(HttpStatus.NOT_FOUND, "LEARNING_STAGE_NOT_FOUND", "学习阶段不存在");
    }

    public static LearningApiException unitNotFound() {
        return new LearningApiException(HttpStatus.NOT_FOUND, "LEARNING_UNIT_NOT_FOUND", "学习单元不存在");
    }

    public static LearningApiException invalidTransition() {
        return new LearningApiException(HttpStatus.CONFLICT, "LEARNING_INVALID_TRANSITION", "学习路线状态转换非法");
    }

    public static LearningApiException archived() {
        return new LearningApiException(HttpStatus.CONFLICT, "LEARNING_ARCHIVED", "已归档学习路线不可修改");
    }
}
