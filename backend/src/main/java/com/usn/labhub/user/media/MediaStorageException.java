package com.usn.labhub.user.media;

public class MediaStorageException extends RuntimeException {

    public enum Reason {
        TOO_LARGE,
        TYPE_UNSUPPORTED,
        DECODE_FAILED,
        PROCESSING_FAILED,
        STORAGE_FAILED,
        INVALID_OBJECT_KEY
    }

    private final Reason reason;

    private MediaStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    static MediaStorageException tooLarge() {
        return new MediaStorageException(Reason.TOO_LARGE, "Media exceeds the configured size limit", null);
    }

    static MediaStorageException typeUnsupported() {
        return new MediaStorageException(Reason.TYPE_UNSUPPORTED, "Media type is unsupported", null);
    }

    static MediaStorageException processingFailed() {
        return new MediaStorageException(Reason.PROCESSING_FAILED, "Media processing failed", null);
    }

    static MediaStorageException storageFailed(Throwable cause) {
        return new MediaStorageException(Reason.STORAGE_FAILED, "Media storage operation failed", cause);
    }

    static MediaStorageException invalidObjectKey() {
        return new MediaStorageException(Reason.INVALID_OBJECT_KEY, "Media object key is invalid", null);
    }
}
