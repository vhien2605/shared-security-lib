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
            case INVALID_HEADER, INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case AUTH_MISSING, API_KEY_INVALID, HMAC_INVALID, SIGNATURE_EXPIRED, NONCE_REPLAY -> HttpStatus.UNAUTHORIZED;
            case BLACKLISTED, WHITELIST_NOT_MATCHED -> HttpStatus.FORBIDDEN;
            case ENDPOINT_NOT_REGISTERED -> HttpStatus.NOT_FOUND;
            case REQUEST_SIZE_EXCEEDED, RESPONSE_SIZE_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case ENDPOINT_DISABLED, ENDPOINT_INACTIVE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case RESPONSE_TIME_THRESHOLD_EXCEEDED, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public String resultCode(SecurityErrorCode errorCode) {
        return String.valueOf(toHttpStatus(errorCode).value());
    }
}
