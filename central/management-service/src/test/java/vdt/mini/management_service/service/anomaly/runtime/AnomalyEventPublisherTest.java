package vdt.mini.management_service.service.anomaly.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import vdt.mini.management_service.dto.event.AnomalyEvent;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.RuleConfidence;
import vdt.mini.management_service.service.anomaly.rule.RuleSetVersion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnomalyEventPublisherTest {
    @Test
    void publish_shouldSerializeToConfiguredTopic() {
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var properties = AnomalyTestFixtures.properties();
        properties.getKafka().setAnomaliesTopic("security.anomalies.test");
        AnomalyEvent event = new AnomalyEvent("a1", null, Instant.EPOCH, "LOG_RULE_ENGINE", AnomalyType.LATENCY_OUTLIER, "HIGH", null, null,
                "svc-1", "svc", "ep-1", "ep", "INBOUND_HTTP", "INBOUND", AnomalyDecision.ANOMALY, 5, RuleConfidence.HIGH,
                List.of("HIST_LATENCY_001"), List.of("durationMs"), Map.of("durationRobustZ", 6), RuleSetVersion.CURRENT, "log-v1", null,
                Instant.EPOCH, Instant.EPOCH, 1, Instant.EPOCH, Instant.EPOCH, 1, Instant.EPOCH);

        new AnomalyEventPublisher(kafkaTemplate, new ObjectMapper().findAndRegisterModules(), properties).publish(event);

        verify(kafkaTemplate).send(eq("security.anomalies.test"), eq("svc-1"), contains("LATENCY_OUTLIER"));
        verify(kafkaTemplate).send(eq("security.anomalies.test"), eq("svc-1"), contains("\"timestamp\":\"1970-01-01T00:00:00Z\""));
        verify(kafkaTemplate).send(eq("security.anomalies.test"), eq("svc-1"), contains("\"lastSeenAt\":\"1970-01-01T00:00:00Z\""));
    }
}
