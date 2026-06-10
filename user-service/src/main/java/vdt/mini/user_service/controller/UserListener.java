package vdt.mini.user_service.controller;


import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

@Component
public class UserListener {
    @KafkaListener(topics = "user.profile.create")
    @InBoundSecurity(
            name = "mock-listener",
            topic = "user.profile.create",
            protocol = EndpointProtocol.MQ,
            method = EndpointMethod.SUB
    )
    public String mockListenerProfile() {
        return "ok";
    }
}
