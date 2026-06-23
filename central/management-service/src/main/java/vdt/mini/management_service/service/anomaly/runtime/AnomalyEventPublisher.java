package vdt.mini.management_service.service.anomaly.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.AnomalyEvent;

@Service
public class AnomalyEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(AnomalyEventPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AnomalyDetectionProperties properties;

    public AnomalyEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, AnomalyDetectionProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(AnomalyEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Publishing anomaly event topic={} key={} anomalyId={} type={} decision={} rules={}",
                    properties.getKafka().getAnomaliesTopic(), event.serviceId(), event.anomalyId(),
                    event.anomalyType(), event.decision(), event.matchedRules());
            kafkaTemplate.send(properties.getKafka().getAnomaliesTopic(), event.serviceId(), payload)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.warn("Failed to publish anomaly event id={}", event.anomalyId(), exception);
                        } else {
                            log.info("Published anomaly event topic={} key={} anomalyId={} type={} decision={}",
                                    properties.getKafka().getAnomaliesTopic(), event.serviceId(), event.anomalyId(),
                                    event.anomalyType(), event.decision());
                        }
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize anomaly event", exception);
        }
    }
}
