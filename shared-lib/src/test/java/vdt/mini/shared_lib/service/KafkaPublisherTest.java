package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaPublisherTest {
    @Test
    void send_shouldPublishRegistrationControlPlaneTopicWithoutOutboundPolicy() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq("security.endpoint.registration"), eq("{\"endpointId\":\"endpoint-1\"}")))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        KafkaPublisher publisher = new KafkaPublisher(kafkaTemplate, new ObjectMapper());

        publisher.send("security.endpoint.registration", new RegistrationPayload("endpoint-1"));

        verify(kafkaTemplate).send("security.endpoint.registration", "{\"endpointId\":\"endpoint-1\"}");
    }

    private record RegistrationPayload(String endpointId) {
    }
}
