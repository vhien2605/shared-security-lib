package vdt.mini.user_service.controller;

import org.apache.kafka.common.errors.NetworkException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

@Component
public class UserMqPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public UserMqPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @OutBoundSecurity(
            name = "IT Void Publish",
            topic = "it.user.outbound.void",
            method = EndpointMethod.PUB,
            protocol = EndpointProtocol.MQ)
    public void publishSuccess(String value) {
        kafkaTemplate.send("it.user.outbound.void", value);
    }

    @OutBoundSecurity(
            name = "IT Retry Publish",
            topic = "it.user.outbound.retry",
            method = EndpointMethod.PUB,
            protocol = EndpointProtocol.MQ)
    public void publishFailure(String value) {
        throw new NetworkException("Simulated broker failure for outbound MQ demo");
    }

    @OutBoundSecurity(
            name = "IT Slow Publish",
            topic = "it.user.outbound.threshold",
            method = EndpointMethod.PUB,
            protocol = EndpointProtocol.MQ)
    public void publishSlow(String value) {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        kafkaTemplate.send("it.user.outbound.threshold", value);
    }
}
