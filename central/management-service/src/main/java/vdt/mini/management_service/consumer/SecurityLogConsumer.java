package vdt.mini.management_service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vdt.mini.management_service.dto.event.SecurityLogEventMessage;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyDetectionService;
import vdt.mini.management_service.service.anomaly.runtime.SecurityLogValidator;

@Component
@ConditionalOnProperty(prefix = "anomaly", name = "enabled", havingValue = "true")
public class SecurityLogConsumer {
    private static final Logger log = LoggerFactory.getLogger(SecurityLogConsumer.class);
    private final ObjectMapper objectMapper;
    private final SecurityLogValidator validator;
    private final AnomalyDetectionService anomalyDetectionService;

    public SecurityLogConsumer(ObjectMapper objectMapper, SecurityLogValidator validator, AnomalyDetectionService anomalyDetectionService) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @KafkaListener(topics = "${anomaly.kafka.logs-topic:security.logs}", groupId = "${spring.kafka.consumer.group-id:management-group}")
    public void consume(String message) {
        try {
            SecurityLogEventMessage event = objectMapper.readValue(message, SecurityLogEventMessage.class);
            if (!validator.isValid(event)) {
                log.warn("Skipping invalid security log message");
                return;
            }
            anomalyDetectionService.process(event);
        } catch (JsonProcessingException exception) {
            log.warn("Skipping malformed security log JSON", exception);
        }
    }
}
