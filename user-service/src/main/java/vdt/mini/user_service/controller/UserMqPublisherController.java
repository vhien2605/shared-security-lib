package vdt.mini.user_service.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserMqPublisherController {

    private static final String TEST_TOPIC = "test.outbound.mq";

    private final KafkaTemplate<String, String> securityKafkaTemplate;

    public UserMqPublisherController(
            @Qualifier("securityKafkaTemplate") KafkaTemplate<String, String> securityKafkaTemplate) {
        this.securityKafkaTemplate = securityKafkaTemplate;
    }

    @PostMapping("/publish-mq-test")
    @OutBoundSecurity(
            name = "test-mq-publisher",
            topic = TEST_TOPIC,
            protocol = EndpointProtocol.MQ,
            method = EndpointMethod.PUB,
            description = "Manual test for outbound MQ retry"
    )
    public Map<String, Object> publishMqTest(@RequestBody Map<String, String> request) {
        String payload = request.getOrDefault("payload", "test-message-" + UUID.randomUUID());

        securityKafkaTemplate.send(TEST_TOPIC, payload);

        return Map.of(
                "status", "published",
                "topic", TEST_TOPIC,
                "payload", payload
        );
    }
}
