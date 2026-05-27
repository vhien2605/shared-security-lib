package vdt.mini.user_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

@FeignClient(name = "mock-profile", url = "https://localhost:8083/profile")
public interface ProfileClient {
    @PostMapping("/api/info")
    @OutBoundSecurity(
            name = "profile-outbound",
            targetUrl = "https://localhost:8083/profile/api/info",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST,
            description = "Gọi API thanh toán đối tác"
    )
    String profile(@RequestBody String body);
}

