package com.usn.labhub.user.common.auth;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

    private final int httpStatus;
    private final AuthReason reason;

    private AuthException(int httpStatus, AuthReason reason, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.reason = reason;
    }

    public static AuthException unauthorized(AuthReason reason, String message) {
        return new AuthException(401, reason, message);
    }

    public static AuthException forbidden(AuthReason reason, String message) {
        return new AuthException(403, reason, message);
    }
}
