package vdt.mini.user_service.controller;


import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

@RestController
public class UserController {
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
}
