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
        String targetUrl,
        String topic,
        String consumerGroup,
        String clientId,
        String clientKey,
        String sourceIp,
        String authType,
        String denyReason,
        String alertSeverity,
        SecurityResultStatus status,
        String resultCode,
        SecurityErrorCode errorCode,
        Long requestSizeBytes,
        Long messageSizeBytes,
        Long responseSizeBytes,
        Long durationMs,
        Integer thresholdMs,
        Integer timeoutMs,
        Integer rateLimit,
        Integer rateLimitWindowSeconds,
        Long remainingQuota,
        Integer retentionDays,
        String retentionBucket,
        Integer retryCount,
        Integer retryAttempt,
        Integer retryBackoffMs,
        String rollbackStrategy) {
}
