package vdt.mini.shared_lib.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.enums.SecurityErrorCode;

@Component
public class SecurityStatusMapper {
    public HttpStatus toHttpStatus(SecurityErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.OK;
        }
        return switch (errorCode) {
            case INVALID_HEADER, INVALID_REQUEST, INVALID_MESSAGE -> HttpStatus.BAD_REQUEST;
            case AUTH_MISSING, API_KEY_INVALID, HMAC_INVALID, SIGNATURE_EXPIRED, NONCE_REPLAY -> HttpStatus.UNAUTHORIZED;
            case BLACKLISTED, WHITELIST_NOT_MATCHED -> HttpStatus.FORBIDDEN;
            case ENDPOINT_NOT_REGISTERED, LISTENER_NOT_REGISTERED -> HttpStatus.NOT_FOUND;
            case REQUEST_SIZE_EXCEEDED, RESPONSE_SIZE_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case ENDPOINT_DISABLED, ENDPOINT_INACTIVE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case RESPONSE_TIME_THRESHOLD_EXCEEDED, CONSUME_FAILED, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public String resultCode(SecurityErrorCode errorCode) {
        return String.valueOf(toHttpStatus(errorCode).value());
    }

    public String mqResultCode(SecurityErrorCode errorCode) {
        if (errorCode == null) {
            return "SEC-200";
        }
        return switch (errorCode) {
            case INVALID_MESSAGE, INVALID_HEADER, INVALID_REQUEST -> "SEC-400";
            case AUTH_MISSING -> "SEC-401";
            case API_KEY_INVALID, HMAC_INVALID, SIGNATURE_EXPIRED, NONCE_REPLAY, BLACKLISTED, WHITELIST_NOT_MATCHED -> "SEC-403";
            case ENDPOINT_NOT_REGISTERED, LISTENER_NOT_REGISTERED -> "SEC-404";
            case REQUEST_SIZE_EXCEEDED, RESPONSE_SIZE_EXCEEDED -> "SEC-413";
            case RATE_LIMIT_EXCEEDED -> "SEC-429";
            case ENDPOINT_DISABLED, ENDPOINT_INACTIVE -> "SEC-503";
            case TIMEOUT_EXCEEDED -> "SEC-504";
            case CONSUME_FAILED -> "SEC-561";
            case RESPONSE_TIME_THRESHOLD_EXCEEDED, INTERNAL_ERROR -> "SEC-500";
        };
    }
}
