package com.usn.labhub.user.collection;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class CollectionApiException extends RuntimeException {
    private final HttpStatus status;
    private final String reason;
    private final Object data;

    private CollectionApiException(HttpStatus status, String reason, String message, Object data) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.data = data;
    }

    public static CollectionApiException invalid() {
        return new CollectionApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "参数错误", null);
    }

    public static CollectionApiException projectNotFound() {
        return new CollectionApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在", null);
    }

    public static CollectionApiException planNotFound() {
        return new CollectionApiException(HttpStatus.NOT_FOUND, "COLLECTION_PLAN_NOT_FOUND", "采集计划不存在", null);
    }

    public static CollectionApiException batchNotFound() {
        return new CollectionApiException(HttpStatus.NOT_FOUND, "COLLECTION_BATCH_NOT_FOUND", "采集批次不存在", null);
    }

    public static CollectionApiException denied() {
        return new CollectionApiException(HttpStatus.FORBIDDEN, "COLLECTION_OPERATION_DENIED", "无采集操作权限", null);
    }

    public static CollectionApiException versionConflict(int currentVersion) {
        return new CollectionApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "数据已更新，请刷新后重试",
                Map.of("currentVersion", currentVersion));
    }

    public static CollectionApiException idempotencyReused() {
        return new CollectionApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同请求", null);
    }

    public static CollectionApiException planNotReady() {
        return new CollectionApiException(HttpStatus.CONFLICT, "COLLECTION_PLAN_NOT_READY", "采集计划尚未就绪", null);
    }

    public static CollectionApiException batchInvalidTransition() {
        return new CollectionApiException(HttpStatus.CONFLICT, "BATCH_INVALID_TRANSITION", "采集批次状态转换非法", null);
    }

    public static CollectionApiException batchImmutable() {
        return new CollectionApiException(HttpStatus.CONFLICT, "BATCH_IMMUTABLE", "采集批次已冻结", null);
    }

    public static CollectionApiException projectState(String state) {
        return new CollectionApiException(HttpStatus.CONFLICT, "PROJECT_" + state, "当前项目状态不允许采集写入", null);
    }
}
