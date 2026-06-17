package vdt.mini.shared_lib.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import vdt.mini.shared_lib.config.SecurityAuditLogProperties;
import vdt.mini.shared_lib.enums.SecurityDirection;
import vdt.mini.shared_lib.enums.SecurityFlowType;
import vdt.mini.shared_lib.enums.SecurityResultStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(topics = "security.logs", partitions = 1)
class SecurityAuditLogPublisherIntegrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publish_shouldWriteSecurityLogJsonToEmbeddedKafka(EmbeddedKafkaBroker embeddedKafka) throws Exception {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
        SecurityAuditLogPublisher publisher = new SecurityAuditLogPublisher(kafkaTemplate, objectMapper, new SecurityAuditLogProperties());

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("security-audit-it", "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "security.logs");

            publisher.publish(event());

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "security.logs");
            JsonNode json = objectMapper.readTree(record.value());
            assertThat(record.key()).isEqualTo("trace-it");
            assertThat(json.get("traceId").asText()).isEqualTo("trace-it");
            assertThat(json.get("retentionBucket").asText()).isEqualTo("r30");
            assertThat(json.get("alertSeverity").asText()).isEqualTo("INFO");
            assertThat(json.get("flowType").asText()).isEqualTo("INBOUND_HTTP");
        }
    }

    private static SecurityLogEvent event() {
        return new SecurityLogEvent("2026-06-16T00:00:00Z", "trace-it", "corr-it",
                SecurityFlowType.INBOUND_HTTP, SecurityDirection.INBOUND, "service-1", "user-service", "endpoint-1",
                "Create User", "HTTP", "GET", "/users", null, null, null, "client-1", "client-key",
                "127.0.0.1", "API_KEY", null, "INFO", SecurityResultStatus.SUCCESS, "200", null, 1, 1, 1,
                10, null, null, null, null, null, 30, "r30", null, null, null, null);
    }
}
