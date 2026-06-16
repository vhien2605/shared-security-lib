package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ClientAuthConfigCreateRequest {
    private String serviceId;
    private String type;
    private String algorithm;
    private LocalDateTime expiresAt;
}
