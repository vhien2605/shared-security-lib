package vdt.mini.user_service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.user_service.client.ProfileClient;

import java.util.Map;

@RestController
public class UserController {

    private final ProfileClient profileClient;
    private final UserMqPublisher userMqPublisher;

    public UserController(ProfileClient profileClient, UserMqPublisher userMqPublisher) {
        this.profileClient = profileClient;
        this.userMqPublisher = userMqPublisher;
    }

    @PostMapping("/users/webhook")
    @InBoundSecurity(
            name = "mock-webhook",
            path = "/users/webhook",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST)
    public String mockWebhook() {
        return "ok";
    }


    @PostMapping("/users/update-v2")
    @InBoundSecurity(
            name = "mock-webhook-PUT",
            path = "/users/update-v2",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.PUT)
    public String mockwebhook2() {
        return "ok";
    }

    @PostMapping("/users/call-outbound")
    public String callOutbound(@RequestBody String body,
                               @RequestHeader(value = "X-Simulate", defaultValue = "success") String simulate) {
        return profileClient.profile(body, simulate);
    }

    @PostMapping("/users/publish-mq")
    public Map<String, Object> publishMq(@RequestBody String body,
                                         @RequestHeader(value = "X-Simulate", defaultValue = "success") String simulate) {
        long start = System.currentTimeMillis();
        switch (simulate.trim().toLowerCase()) {
            case "failure" -> userMqPublisher.publishFailure(body);
            case "timeout" -> userMqPublisher.publishSlow(body);
            default -> userMqPublisher.publishSuccess(body);
        }
        long duration = System.currentTimeMillis() - start;
        return Map.of("status", "published", "simulate", simulate, "durationMs", duration);
    }
}
