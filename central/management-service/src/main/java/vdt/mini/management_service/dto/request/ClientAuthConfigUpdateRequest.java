package vdt.mini.management_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ClientAuthConfigUpdateRequest {
    private String authConfigId;
    private String algorithm;
    private LocalDateTime expiresAt;
    private Boolean enabled;
}
