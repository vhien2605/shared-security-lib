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

    private SecurityLogEvent event(SecurityRequestContext context, SecurityResultStatus status, SecurityErrorCode errorCode) {
        SecurityRequestContext safe = context == null ? new SecurityRequestContext() : context;
        Integer retentionDays = safe.getRetentionDays() == null ? 30 : safe.getRetentionDays();
        return new SecurityLogEvent(
                Instant.now().toString(),
                safe.getTraceId(),
                safe.getCorrelationId(),
                SecurityFlowType.INBOUND_HTTP,
                SecurityDirection.INBOUND,
                safe.getServiceId(),
                safe.getServiceName(),
                safe.getEndpointId(),
                safe.getEndpointName(),
                safe.getProtocol(),
                safe.getMethod(),
                safe.getPath(),
                safe.getClientId(),
                safe.getClientKey(),
                safe.getSourceIp(),
                status,
                errorCode == null ? "200" : statusMapper.resultCode(errorCode),
                errorCode,
                safe.getRequestSizeBytes(),
                safe.getResponseSizeBytes(),
                safe.getDurationMs(),
                safe.getThresholdMs(),
                safe.getTimeoutMs(),
                safe.getRateLimit(),
                safe.getRateLimitWindowSeconds(),
                safe.getRemainingQuota(),
                retentionDays,
                retentionDays + "d");
    }
}
