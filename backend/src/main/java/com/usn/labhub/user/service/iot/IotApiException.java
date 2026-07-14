package com.usn.labhub.user.service.iot;

public class IotApiException extends RuntimeException {
    private final int code;

    public IotApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
