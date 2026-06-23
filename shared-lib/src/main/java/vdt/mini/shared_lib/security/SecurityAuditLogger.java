package vdt.mini.shared_lib.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vdt.mini.shared_lib.enums.SecurityDirection;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityFlowType;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundExecutionPolicy;

import java.time.Instant;

@Service
public class SecurityAuditLogger {
    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger internalLog = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private final ObjectMapper objectMapper;
    private final SecurityStatusMapper statusMapper;
    private final SecurityAuditLogPublisher publisher;

    public SecurityAuditLogger(ObjectMapper objectMapper, SecurityStatusMapper statusMapper) {
        this(objectMapper, statusMapper, (SecurityAuditLogPublisher) null);
    }

    @Autowired
    public SecurityAuditLogger(ObjectMapper objectMapper, SecurityStatusMapper statusMapper,
                               ObjectProvider<SecurityAuditLogPublisher> publisherProvider) {
        this(objectMapper, statusMapper, publisherProvider == null ? null : publisherProvider.getIfAvailable());
    }

    public SecurityAuditLogger(ObjectMapper objectMapper, SecurityStatusMapper statusMapper,
                               SecurityAuditLogPublisher publisher) {
        this.objectMapper = objectMapper;
        this.statusMapper = statusMapper;
        this.publisher = publisher;
    }

    public void log(SecurityRequestContext context, SecurityResultStatus status, SecurityErrorCode errorCode) {
        SecurityLogEvent event = event(context, status, errorCode);
        writeAndPublish(event, "security_audit_log_serialization_failed",
                context == null ? null : context.getEndpointId(), errorCode);
    }

    public void logOutbound(OutboundExecutionPolicy policy, OutboundContext context, SecurityResultStatus status,
                            SecurityErrorCode errorCode, long durationMs, Integer retryAttempt) {
        SecurityLogEvent event = outboundEvent(policy, context, status, errorCode, durationMs, retryAttempt);
        writeAndPublish(event, "security_outbound_audit_log_serialization_failed",
                policy == null ? null : policy.endpointId(), errorCode);
    }

    private void writeAndPublish(SecurityLogEvent event, String serializationFailureMessage,
                                 String endpointId, SecurityErrorCode errorCode) {
        try {
            auditLog.info(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            auditLog.warn("{} endpointId={} errorCode={}", serializationFailureMessage, endpointId, errorCode, ex);
        }
        if (publisher == null) {
            return;
        }
        try {
            publisher.publish(event);
        } catch (RuntimeException ex) {
            internalLog.warn("security_audit_log_publisher_unexpected_failure endpointId={} errorCode={}",
                    endpointId, errorCode, ex);
        }
    }

    private SecurityLogEvent event(SecurityRequestContext context, SecurityResultStatus status, SecurityErrorCode errorCode) {
        SecurityRequestContext safe = context == null ? new SecurityRequestContext() : context;
        Integer retentionDays = SecurityLogRetentionBucketMapper.normalizedDays(safe.getRetentionDays());
        SecurityFlowType flowType = "MQ".equalsIgnoreCase(safe.getProtocol())
                ? SecurityFlowType.INBOUND_MQ_LISTENER
                : SecurityFlowType.INBOUND_HTTP;
        String resultCode = flowType == SecurityFlowType.INBOUND_MQ_LISTENER
                ? statusMapper.mqResultCode(errorCode)
                : (errorCode == null ? "200" : statusMapper.resultCode(errorCode));
        return new SecurityLogEvent(
                Instant.now().toString(),
                safe.getTraceId(),
                safe.getCorrelationId(),
                flowType,
                SecurityDirection.INBOUND,
                safe.getServiceId(),
                safe.getServiceName(),
                safe.getEndpointId(),
                safe.getEndpointName(),
                safe.getProtocol(),
                safe.getMethod(),
                safe.getPath(),
                null,
                safe.getTopic(),
                safe.getConsumerGroup(),
                safe.getClientId(),
                safe.getClientKey(),
                safe.getSourceIp(),
                safe.getAuthType(),
                safe.getDenyReason(),
                safe.getAlertSeverity(),
                status,
                resultCode,
                errorCode,
                flowType == SecurityFlowType.INBOUND_MQ_LISTENER ? null : safe.getRequestSizeBytes(),
                flowType == SecurityFlowType.INBOUND_MQ_LISTENER ? safe.getRequestSizeBytes() : null,
                flowType == SecurityFlowType.INBOUND_MQ_LISTENER ? null : safe.getResponseSizeBytes(),
                safe.getDurationMs(),
                safe.getThresholdMs(),
                safe.getTimeoutMs(),
                safe.getRateLimit(),
                safe.getRateLimitWindowSeconds(),
                safe.getRemainingQuota(),
                retentionDays,
                SecurityLogRetentionBucketMapper.bucket(retentionDays),
                null,
                null,
                null,
                null);
    }

    private SecurityLogEvent outboundEvent(OutboundExecutionPolicy policy, OutboundContext context,
                                           SecurityResultStatus status, SecurityErrorCode errorCode,
                                           long durationMs, Integer retryAttempt) {
        Integer retentionDays = SecurityLogRetentionBucketMapper.normalizedDays(
                policy == null ? null : policy.logRetentionDays());
        return new SecurityLogEvent(
                Instant.now().toString(),
                context == null ? null : context.traceId(),
                context == null ? null : context.correlationId(),
                "MQ".equalsIgnoreCase(policy == null ? null : policy.protocol())
                        ? SecurityFlowType.OUTBOUND_MQ
                        : SecurityFlowType.OUTBOUND_HTTP,
                SecurityDirection.OUTBOUND,
                policy == null ? null : policy.serviceId(),
                policy == null ? null : policy.serviceName(),
                policy == null ? null : policy.endpointId(),
                policy == null ? null : policy.endpointName(),
                policy == null ? null : policy.protocol(),
                policy == null ? null : policy.method(),
                null,
                policy == null ? null : policy.targetUrl(),
                policy == null ? null : policy.topic(),
                null,
                null,
                null,
                null,
                null,
                null,
                policy == null ? null : policy.alertSeverity(),
                status,
                errorCode == null ? "200" : statusMapper.resultCode(errorCode),
                errorCode,
                null,
                null,
                null,
                durationMs,
                policy == null ? null : policy.responseTimeThresholdMs(),
                policy == null ? null : policy.timeoutMs(),
                null,
                null,
                null,
                retentionDays,
                SecurityLogRetentionBucketMapper.bucket(retentionDays),
                policy == null ? null : policy.retryCount(),
                retryAttempt,
                policy == null ? null : policy.retryBackoffMs(),
                policy == null ? null : policy.rollbackStrategy());
    }
}
