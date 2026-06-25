package vdt.mini.management_service.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vdt.mini.management_service.entity.SecurityEventLog;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityLogEventMessage {
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

    public AnomalyGroupKey groupKey() {
        return new AnomalyGroupKey(serviceId, endpointId, flowType);
    }

    public static SecurityLogEventMessage from(SecurityEventLog log) {
        SecurityLogEventMessage message = new SecurityLogEventMessage();
        message.timestamp = log.getTimestamp();
        message.traceId = log.getTraceId();
        message.correlationId = log.getCorrelationId();
        message.flowType = log.getFlowType();
        message.direction = log.getDirection();
        message.serviceId = log.getServiceId();
        message.serviceName = log.getServiceName();
        message.endpointId = log.getEndpointId();
        message.endpointName = log.getEndpointName();
        message.protocol = log.getProtocol();
        message.method = log.getMethod();
        message.path = log.getPath();
        message.targetUrl = log.getTargetUrl();
        message.topic = log.getTopic();
        message.consumerGroup = log.getConsumerGroup();
        message.producerClientId = log.getProducerClientId();
        message.clientId = log.getClientId();
        message.clientKey = log.getClientKey();
        message.sourceIp = log.getSourceIp();
        message.authType = log.getAuthType();
        message.denyReason = log.getDenyReason();
        message.alertSeverity = log.getAlertSeverity();
        message.status = log.getStatus();
        message.resultCode = log.getResultCode();
        message.errorCode = log.getErrorCode();
        message.requestSizeBytes = log.getRequestSizeBytes();
        message.messageSizeBytes = log.getMessageSizeBytes();
        message.responseSizeBytes = log.getResponseSizeBytes();
        message.durationMs = log.getDurationMs();
        message.thresholdMs = log.getThresholdMs();
        message.timeoutMs = log.getTimeoutMs();
        message.rateLimit = log.getRateLimit();
        message.rateLimitWindowSeconds = log.getRateLimitWindowSeconds();
        message.remainingQuota = log.getRemainingQuota();
        message.retentionDays = log.getRetentionDays();
        message.retentionBucket = log.getRetentionBucket();
        message.retryCount = log.getRetryCount();
        message.retryAttempt = log.getRetryAttempt();
        message.retryBackoffMs = log.getRetryBackoffMs();
        message.rollbackStrategy = log.getRollbackStrategy();
        return message;
    }
}
