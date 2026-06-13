package vdt.mini.user_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

@FeignClient(name = "profile-service", url = "http://localhost:8082/profile")
public interface ProfileClient {
    @PostMapping("/api/info")
    @OutBoundSecurity(
            name = "profile-outbound",
            targetUrl = "http://localhost:8082/profile/api/info",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST,
            description = "Gọi API profile để test retry/timeout"
    )
    String profile(@RequestBody String body,
                   @RequestHeader("X-Simulate") String simulate);
}

