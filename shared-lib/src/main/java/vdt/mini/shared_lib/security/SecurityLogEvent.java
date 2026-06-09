package vdt.mini.shared_lib.security;

import vdt.mini.shared_lib.enums.SecurityDirection;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityFlowType;
import vdt.mini.shared_lib.enums.SecurityResultStatus;

public record SecurityLogEvent(
        String timestamp,
        String traceId,
        String correlationId,
        SecurityFlowType flowType,
        SecurityDirection direction,
        String serviceId,
        String serviceName,
        String endpointId,
        String endpointName,
        String protocol,
        String method,
        String path,
        String clientId,
        String clientKey,
        String sourceIp,
        SecurityResultStatus status,
        String resultCode,
        SecurityErrorCode errorCode,
        long requestSizeBytes,
        long responseSizeBytes,
        long durationMs,
        Integer thresholdMs,
        Integer timeoutMs,
        Integer rateLimit,
        Integer rateLimitWindowSeconds,
        Long remainingQuota,
        Integer retentionDays,
        String retentionBucket) {
}
