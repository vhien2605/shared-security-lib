package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SecurityLogResponse {
    private String id;
    private String timestamp;
    private String traceId;
    private String correlationId;
    private String flowType;
    private String direction;
    private String serviceId;
    private String serviceName;
    private String endpointId;
    private String endpointName;
    private String protocol;
    private String method;
    private String path;
    private String targetUrl;
    private String topic;
    private String consumerGroup;
    private String producerClientId;
    private String clientId;
    private String clientKey;
    private String sourceIp;
    private String authType;
    private String denyReason;
    private String alertSeverity;
    private String status;
    private String resultCode;
    private String errorCode;
    private Long requestSizeBytes;
    private Long messageSizeBytes;
    private Long responseSizeBytes;
    private Long durationMs;
    private Integer thresholdMs;
    private Integer timeoutMs;
    private Integer rateLimit;
    private Integer rateLimitWindowSeconds;
    private Long remainingQuota;
    private Integer retentionDays;
    private String retentionBucket;
    private Integer retryCount;
    private Integer retryAttempt;
    private Integer retryBackoffMs;
    private String rollbackStrategy;
}
