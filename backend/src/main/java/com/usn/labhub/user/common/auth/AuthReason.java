package com.usn.labhub.user.common.auth;

public enum AuthReason {
    TOKEN_MISSING,
    TOKEN_INVALID,
    TOKEN_EXPIRED,
    ACCOUNT_DISABLED,
    ACCESS_DENIED,
    RESOURCE_NOT_FOUND
}
