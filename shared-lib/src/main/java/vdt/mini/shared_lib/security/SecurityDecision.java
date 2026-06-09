package vdt.mini.shared_lib.security;

import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;

public record SecurityDecision(
        boolean allowed,
        SecurityResultStatus status,
        SecurityErrorCode errorCode,
        String message,
        String endpointId,
        String clientId,
        String clientKey) {

    public static SecurityDecision allow(String endpointId, String clientId, String clientKey) {
        return new SecurityDecision(true, SecurityResultStatus.SUCCESS, null, "OK", endpointId, clientId, clientKey);
    }

    public static SecurityDecision deny(SecurityResultStatus status, SecurityErrorCode errorCode, String message,
                                        String endpointId, String clientId, String clientKey) {
        return new SecurityDecision(false, status, errorCode, message, endpointId, clientId, clientKey);
    }
}
