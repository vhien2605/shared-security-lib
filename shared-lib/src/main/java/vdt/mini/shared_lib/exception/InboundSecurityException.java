package vdt.mini.shared_lib.exception;

import vdt.mini.shared_lib.enums.SecurityErrorCode;

public class InboundSecurityException extends RuntimeException {
    private final SecurityErrorCode errorCode;

    public InboundSecurityException(SecurityErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SecurityErrorCode getErrorCode() {
        return errorCode;
    }
}
