package vdt.mini.shared_lib.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import vdt.mini.shared_lib.config.SecurityAuditLogProperties;
import vdt.mini.shared_lib.enums.SecurityDirection;
import vdt.mini.shared_lib.enums.SecurityFlowType;
import vdt.mini.shared_lib.enums.SecurityResultStatus;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditLogPublisherTest {
    @Test
    void publish_shouldSendJsonToLibraryTopicWithTraceIdKey() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq(SecurityAuditLogPublisher.TOPIC), eq("trace-1"), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        SecurityAuditLogProperties properties = new SecurityAuditLogProperties();
        SecurityAuditLogPublisher publisher = new SecurityAuditLogPublisher(kafkaTemplate, new ObjectMapper(), properties);

        publisher.publish(event("trace-1", "corr-1", "endpoint-1", "service-1"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(SecurityAuditLogPublisher.TOPIC), eq("trace-1"), payload.capture());
        assertThat(payload.getValue()).contains("\"traceId\":\"trace-1\"");
    }

    @Test
    void publish_shouldFallbackKeyToCorrelationEndpointAndService() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(String.class), any(), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        SecurityAuditLogPublisher publisher = new SecurityAuditLogPublisher(kafkaTemplate, new ObjectMapper(), new SecurityAuditLogProperties());

        publisher.publish(event(null, "corr-1", "endpoint-1", "service-1"));
        publisher.publish(event("", null, "endpoint-1", "service-1"));
        publisher.publish(event(null, null, null, "service-1"));

        verify(kafkaTemplate).send(eq(SecurityAuditLogPublisher.TOPIC), eq("corr-1"), any(String.class));
        verify(kafkaTemplate).send(eq(SecurityAuditLogPublisher.TOPIC), eq("endpoint-1"), any(String.class));
        verify(kafkaTemplate).send(eq(SecurityAuditLogPublisher.TOPIC), eq("service-1"), any(String.class));
    }

    @Test
    void publish_shouldNotThrow_whenDisabledNullSerializationSendOrCallbackFails() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        SecurityAuditLogProperties disabled = new SecurityAuditLogProperties();
        disabled.setEnabled(false);
        SecurityAuditLogPublisher disabledPublisher = new SecurityAuditLogPublisher(kafkaTemplate, objectMapper, disabled);

        assertDoesNotThrow(() -> disabledPublisher.publish(event("trace-1", null, null, null)));
        assertDoesNotThrow(() -> disabledPublisher.publish(null));
        verify(kafkaTemplate, never()).send(any(String.class), any(), any(String.class));

        SecurityAuditLogProperties enabled = new SecurityAuditLogProperties();
        SecurityAuditLogPublisher serializationPublisher = new SecurityAuditLogPublisher(kafkaTemplate, objectMapper, enabled);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") { });
        assertDoesNotThrow(() -> serializationPublisher.publish(event("trace-1", null, null, null)));

        ObjectMapper realMapper = new ObjectMapper();
        SecurityAuditLogPublisher sendPublisher = new SecurityAuditLogPublisher(kafkaTemplate, realMapper, enabled);
        when(kafkaTemplate.send(any(String.class), any(), any(String.class))).thenThrow(new IllegalStateException("down"));
        assertDoesNotThrow(() -> sendPublisher.publish(event("trace-2", null, null, null)));

        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("ack failed"));
        when(kafkaTemplate.send(any(String.class), any(), any(String.class))).thenReturn((CompletableFuture) failed);
        assertDoesNotThrow(() -> sendPublisher.publish(event("trace-3", null, null, null)));
    }

    private static SecurityLogEvent event(String traceId, String correlationId, String endpointId, String serviceId) {
        return new SecurityLogEvent("2026-06-16T00:00:00Z", traceId, correlationId,
                SecurityFlowType.INBOUND_HTTP, SecurityDirection.INBOUND, serviceId, "user-service", endpointId,
                "Create User", "HTTP", "GET", "/users", null, null, null, "client-1", "client-key",
                "127.0.0.1", "API_KEY", null, "INFO", SecurityResultStatus.SUCCESS, "200", null, 1L, 1L, 1L,
                10L, null, null, null, null, null, 30, "r30", null, null, null, null);
    }
}
