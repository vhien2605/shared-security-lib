package vdt.mini.shared_lib.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ObjectMapper objectMapper;
    private final SecurityStatusMapper statusMapper;

    public SecurityAuditLogger(ObjectMapper objectMapper, SecurityStatusMapper statusMapper) {
        this.objectMapper = objectMapper;
        this.statusMapper = statusMapper;
    }

    public void log(SecurityRequestContext context, SecurityResultStatus status, SecurityErrorCode errorCode) {
        SecurityLogEvent event = event(context, status, errorCode);
        try {
            auditLog.info(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            auditLog.warn("security_audit_log_serialization_failed endpointId={} errorCode={}",
                    context == null ? null : context.getEndpointId(), errorCode, ex);
        }
    }

    public void logOutbound(OutboundExecutionPolicy policy, OutboundContext context, SecurityResultStatus status,
                            SecurityErrorCode errorCode, long durationMs, Integer retryAttempt) {
        SecurityLogEvent event = outboundEvent(policy, context, status, errorCode, durationMs, retryAttempt);
        try {
            auditLog.info(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            auditLog.warn("security_outbound_audit_log_serialization_failed endpointId={} errorCode={}",
                    policy == null ? null : policy.endpointId(), errorCode, ex);
        }
    }

    private SecurityLogEvent event(SecurityRequestContext context, SecurityResultStatus status, SecurityErrorCode errorCode) {
        SecurityRequestContext safe = context == null ? new SecurityRequestContext() : context;
        Integer retentionDays = safe.getRetentionDays() == null ? 30 : safe.getRetentionDays();
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
                status,
                resultCode,
                errorCode,
                safe.getRequestSizeBytes(),
                safe.getRequestSizeBytes(),
                safe.getResponseSizeBytes(),
                safe.getDurationMs(),
                safe.getThresholdMs(),
                safe.getTimeoutMs(),
                safe.getRateLimit(),
                safe.getRateLimitWindowSeconds(),
                safe.getRemainingQuota(),
                retentionDays,
                retentionDays + "d",
                null,
                null,
                null,
                null);
    }

    private SecurityLogEvent outboundEvent(OutboundExecutionPolicy policy, OutboundContext context,
                                           SecurityResultStatus status, SecurityErrorCode errorCode,
                                           long durationMs, Integer retryAttempt) {
        Integer retentionDays = policy == null || policy.logRetentionDays() == null ? 30 : policy.logRetentionDays();
        return new SecurityLogEvent(
                Instant.now().toString(),
                context == null ? null : context.traceId(),
                context == null ? null : context.correlationId(),
                SecurityFlowType.OUTBOUND_HTTP,
                SecurityDirection.OUTBOUND,
                policy == null ? null : policy.serviceId(),
                null,
                policy == null ? null : policy.endpointId(),
                policy == null ? null : policy.endpointName(),
                policy == null ? null : policy.protocol(),
                policy == null ? null : policy.method(),
                null,
                policy == null ? null : policy.targetUrl(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                errorCode == null ? "200" : statusMapper.resultCode(errorCode),
                errorCode,
                0,
                0,
                0,
                durationMs,
                policy == null ? null : policy.responseTimeThresholdMs(),
                policy == null ? null : policy.timeoutMs(),
                null,
                null,
                null,
                retentionDays,
                retentionDays + "d",
                policy == null ? null : policy.retryCount(),
                retryAttempt,
                policy == null ? null : policy.retryBackoffMs(),
                policy == null ? null : policy.rollbackStrategy());
    }
}
