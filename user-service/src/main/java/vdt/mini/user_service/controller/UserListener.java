package vdt.mini.user_service.controller;


import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointProtocol;

@Component
public class UserListener {
    @InBoundSecurity(
            name = "mock-listener",
            topic = "user.profile.create",
            protocol = EndpointProtocol.MQ)
    public String mockListenerProfile() {
        return "ok";
    }
}
