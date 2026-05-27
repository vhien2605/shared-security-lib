package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaPublisher(@Qualifier("securityKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(String topic, Object payload) {
        try {
            String jsonString = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, jsonString)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send Kafka message to topic={}", topic, ex);
                        } else {
                            log.info("Sent Kafka message to topic={}, offset={}", topic,
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize Kafka message for topic={}", topic, e);
        }
    }
}
