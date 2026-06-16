package vdt.mini.shared_lib.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vdt.mini.shared_lib.config.SecurityAuditLogProperties;

@Service
public class SecurityAuditLogPublisher {
    public static final String TOPIC = "security.logs";

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditLogProperties properties;

    public SecurityAuditLogPublisher(@Qualifier("securityKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     SecurityAuditLogProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(SecurityLogEvent event) {
        if (!properties.isEnabled() || event == null) {
            return;
        }
        String key = messageKey(event);
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, key, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("security_audit_log_publish_failed topic={} traceId={} endpointId={}",
                            TOPIC, event.traceId(), event.endpointId(), ex);
                }
            });
        } catch (JsonProcessingException ex) {
            log.warn("security_audit_log_serialization_failed_before_publish topic={} traceId={} endpointId={}",
                    TOPIC, event.traceId(), event.endpointId(), ex);
        } catch (RuntimeException ex) {
            log.warn("security_audit_log_send_failed topic={} traceId={} endpointId={}",
                    TOPIC, event.traceId(), event.endpointId(), ex);
        }
    }

    private static String messageKey(SecurityLogEvent event) {
        if (StringUtils.hasText(event.traceId())) {
            return event.traceId();
        }
        if (StringUtils.hasText(event.correlationId())) {
            return event.correlationId();
        }
        if (StringUtils.hasText(event.endpointId())) {
            return event.endpointId();
        }
        return event.serviceId();
    }
}
