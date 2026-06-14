package vdt.mini.shared_lib.exception;

import vdt.mini.shared_lib.enums.OutboundErrorCode;

public class OutboundException extends RuntimeException {
    private final OutboundErrorCode errorCode;
    private final String endpointId;

    public OutboundException(OutboundErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public OutboundException(OutboundErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, null);
    }

    public OutboundException(OutboundErrorCode errorCode, String message, Throwable cause, String endpointId) {
        super(message, cause);
        this.errorCode = errorCode == null ? OutboundErrorCode.INTERNAL_ERROR : errorCode;
        this.endpointId = endpointId;
    }

    public OutboundErrorCode getErrorCode() {
        return errorCode;
    }

    public String getEndpointId() {
        return endpointId;
    }
}
