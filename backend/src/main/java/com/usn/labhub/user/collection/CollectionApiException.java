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

    public static CollectionApiException fileTypeUnsupported() {
        return new CollectionApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE_UNSUPPORTED", "仅支持 CSV 文件", null);
    }

    public static CollectionApiException fileTooLarge() {
        return new CollectionApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "文件超过允许限制", null);
    }

    public static CollectionApiException fileEncodingUnsupported() {
        return new CollectionApiException(HttpStatus.BAD_REQUEST, "FILE_ENCODING_UNSUPPORTED", "文件必须使用 UTF-8 编码", null);
    }

    public static CollectionApiException mappingInvalid() {
        return new CollectionApiException(HttpStatus.BAD_REQUEST, "IMPORT_MAPPING_INVALID", "导入映射非法", null);
    }

    public static CollectionApiException manualInvalid() {
        return new CollectionApiException(HttpStatus.BAD_REQUEST, "MANUAL_RECORD_INVALID", "人工记录非法", null);
    }

    public static CollectionApiException dataImportNotFound() {
        return new CollectionApiException(HttpStatus.NOT_FOUND, "DATA_IMPORT_NOT_FOUND", "导入不存在", null);
    }

    public static CollectionApiException manualRecordNotFound() {
        return new CollectionApiException(HttpStatus.NOT_FOUND, "MANUAL_RECORD_NOT_FOUND", "人工记录不存在", null);
    }

    public static CollectionApiException snapshotNotFound() {
        return new CollectionApiException(HttpStatus.NOT_FOUND, "COLLECTION_SNAPSHOT_NOT_FOUND", "采集快照不存在", null);
    }

    public static CollectionApiException importAlreadyConfirmed() {
        return new CollectionApiException(HttpStatus.CONFLICT, "IMPORT_ALREADY_CONFIRMED", "当前导入修订已确认", null);
    }

    public static CollectionApiException batchNotCompleted() {
        return new CollectionApiException(HttpStatus.CONFLICT, "BATCH_NOT_COMPLETED", "采集批次尚未完成，不能生成快照", null);
    }

    public static CollectionApiException snapshotAlreadyBound() {
        return new CollectionApiException(HttpStatus.CONFLICT, "SNAPSHOT_ALREADY_BOUND", "当前修订已绑定采集快照", null);
    }

    public static CollectionApiException experimentDenied() {
        return new CollectionApiException(HttpStatus.FORBIDDEN, "EXPERIMENT_OPERATION_DENIED", "无实验记录写权限", null);
    }

    public static CollectionApiException revisionImmutable() {
        return new CollectionApiException(HttpStatus.CONFLICT, "EXPERIMENT_REVISION_IMMUTABLE", "实验修订不可修改", null);
    }

    public static CollectionApiException fileStorageFailed() {
        return new CollectionApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "文件保存失败", null);
    }

    public static CollectionApiException importProcessingFailed() {
        return new CollectionApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMPORT_PROCESSING_FAILED", "导入处理失败", null);
    }

    public static CollectionApiException projectState(String state) {
        return new CollectionApiException(HttpStatus.CONFLICT, "PROJECT_" + state, "当前项目状态不允许采集写入", null);
    }
}
