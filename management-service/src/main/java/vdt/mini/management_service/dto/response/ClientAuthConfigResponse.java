package vdt.mini.management_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vdt.mini.management_service.util.enums.AuthType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ClientAuthConfigResponse {
    private String id;
    private String inboundEndpointId;
    private String endpointCode;
    private AuthType type;
    private String algorithm;
    private Boolean enabled;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime disabledAt;
}
