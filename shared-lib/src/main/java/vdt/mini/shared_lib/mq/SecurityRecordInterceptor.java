package vdt.mini.shared_lib.mq;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.security.InboundSecurityDecisionService;
import vdt.mini.shared_lib.security.RedisRateLimiter;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityDecision;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.IdentityManager;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class SecurityRecordInterceptor implements RecordInterceptor<String, Object> {
    private final EndpointRegistry endpointRegistry;
    private final InboundSecurityDecisionService decisionService;
    private final MqSecurityHeaderExtractor headerExtractor;
    private final RedisRateLimiter rateLimiter;
    private final SecurityAuditLogger auditLogger;
    private final IdentityManager identityManager;
    private final String serviceName;

    public SecurityRecordInterceptor(EndpointRegistry endpointRegistry,
                                     InboundSecurityDecisionService decisionService,
                                     MqSecurityHeaderExtractor headerExtractor,
                                     RedisRateLimiter rateLimiter,
                                     SecurityAuditLogger auditLogger,
                                     IdentityManager identityManager,
                                     @Value("${app.security.service.name:my-service}") String serviceName) {
        this.endpointRegistry = endpointRegistry;
        this.decisionService = decisionService;
        this.headerExtractor = headerExtractor;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
        this.identityManager = identityManager;
        this.serviceName = serviceName;
    }

    @Override
    public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        if (record == null) {
            throw new InboundSecurityException(SecurityErrorCode.INVALID_MESSAGE, "Kafka record is null");
        }
        MqSecurityHeaders headers = headerExtractor.extract(record.headers());
        SecurityRequestContext context = buildContext(record, consumer, headers);
        Optional<EndpointRegistry.InboundMqEndpoint> endpoint = endpointRegistry.findInboundMq(record.topic());
        if (endpoint.isEmpty()) {
            return record;
        }
        SecurityDecision decision = decisionService.decide(toRequest(record, headers, context.getRequestSizeBytes()), endpoint.get(), context);
        if (!decision.allowed()) {
            auditLogger.log(context, decision.status(), decision.errorCode());
            throw new InboundSecurityException(decision.errorCode(), decision.message());
        }
        context.setEndpointId(decision.endpointId() == null ? context.getEndpointId() : decision.endpointId());
        context.setClientId(decision.clientId());
        context.setClientKey(decision.clientKey());
        applyRateLimit(context);
        SecurityRequestContextHolder.set(context);
        return record;
    }

    @Override
    public void success(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        SecurityRequestContext context = SecurityRequestContextHolder.get();
        if (context == null || context.getEndpointId() == null || !"MQ".equalsIgnoreCase(context.getProtocol())) {
            return;
        }
        context.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - context.getStartedAtNanos()));
        logTiming(context);
        auditLogger.log(context, SecurityResultStatus.SUCCESS, null);
    }

    @Override
    public void failure(ConsumerRecord<String, Object> record, Exception exception, Consumer<String, Object> consumer) {
        SecurityRequestContext context = SecurityRequestContextHolder.get();
        if (context == null || context.getEndpointId() == null || !"MQ".equalsIgnoreCase(context.getProtocol())) {
            return;
        }
        context.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - context.getStartedAtNanos()));
        if (exception instanceof InboundSecurityException deniedException) {
            auditLogger.log(context, SecurityResultStatus.DENIED, deniedException.getErrorCode());
        } else {
            auditLogger.log(context, SecurityResultStatus.FAILED, SecurityErrorCode.CONSUME_FAILED);
        }
    }

    @Override
    public void afterRecord(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        SecurityRequestContextHolder.clear();
    }

    private SecurityRequestContext buildContext(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer,
                                                MqSecurityHeaders headers) {
        SecurityRequestContext context = new SecurityRequestContext();
        String traceId = firstNonBlank(headers.traceId(), UUID.randomUUID().toString());
        context.setTraceId(traceId);
        context.setCorrelationId(firstNonBlank(headers.correlationId(), traceId));
        context.setServiceId(identityManager.getOrCreateServiceId());
        context.setServiceName(serviceName);
        context.setProtocol("MQ");
        context.setTopic(record.topic());
        context.setConsumerGroup(consumer == null || consumer.groupMetadata() == null ? null : consumer.groupMetadata().groupId());
        context.setRequestSizeBytes(messageSize(record));
        context.setStartedAtNanos(System.nanoTime());
        return context;
    }

    private MqSecurityRequest toRequest(ConsumerRecord<String, Object> record, MqSecurityHeaders headers, long messageSizeBytes) {
        return new MqSecurityRequest(record.topic(), record.key(), record.value(), headers, messageSizeBytes);
    }

    private void applyRateLimit(SecurityRequestContext context) {
        Integer limit = context.getRateLimit();
        Integer windowSeconds = context.getRateLimitWindowSeconds();
        if (limit == null || windowSeconds == null || limit <= 0 || windowSeconds <= 0) {
            return;
        }
        String subject = context.getClientKey() != null ? context.getClientKey() : context.getTopic();
        RedisRateLimiter.RateLimitResult result = rateLimiter.checkMqInbound(
                context.getServiceId(), context.getEndpointId(), subject, limit, windowSeconds);
        context.setRemainingQuota(result.remainingQuota());
        if (!result.allowed()) {
            context.setDenyReason("Rate limit exceeded");
            auditLogger.log(context, SecurityResultStatus.DENIED, SecurityErrorCode.RATE_LIMIT_EXCEEDED);
            throw new InboundSecurityException(SecurityErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded");
        }
    }

    private void logTiming(SecurityRequestContext context) {
        if (context.getTimeoutMs() != null && context.getTimeoutMs() > 0 && context.getDurationMs() > context.getTimeoutMs()) {
            auditLogger.log(context, SecurityResultStatus.TIMEOUT, SecurityErrorCode.TIMEOUT_EXCEEDED);
        } else if (context.getThresholdMs() != null && context.getThresholdMs() > 0 && context.getDurationMs() > context.getThresholdMs()) {
            auditLogger.log(context, SecurityResultStatus.WARN, SecurityErrorCode.RESPONSE_TIME_THRESHOLD_EXCEEDED);
        }
    }

    private long messageSize(ConsumerRecord<String, Object> record) {
        return byteLength(record.key()) + byteLength(record.value());
    }

    private long byteLength(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof byte[] bytes) {
            return bytes.length;
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
